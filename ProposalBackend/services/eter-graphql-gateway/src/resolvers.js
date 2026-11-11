import { GraphQLError, GraphQLScalarType, Kind } from 'graphql';
import { exigirUsuario, resolverNit } from './auth.js';
import { config } from './config.js';
import { llamar } from './http.js';

const RUTA_ACTIVOS = '/api/v1/activos/activos-fijos';
const RUTA_ASIGNACIONES = '/api/v1/activos/asignaciones';
const RUTA_MOVIMIENTOS = '/api/v1/activos/movimientos-inventario';
const RUTA_PRESTAMOS = '/api/v1/activos/prestamos';
const RUTA_NOTIFICACIONES = '/api/v1/notificaciones';

/**
 * Memoiza por peticion: si el frontend pide 50 prestamos con su `activo`,
 * cada placa se consulta una sola vez (evita el problema N+1).
 */
function memo(ctx, clave, fn) {
  ctx.cache ??= new Map();
  if (!ctx.cache.has(clave)) {
    const promesa = fn();
    ctx.cache.set(clave, promesa);
    promesa.catch(() => ctx.cache.delete(clave));
  }
  return ctx.cache.get(clave);
}

const token = (ctx) => exigirUsuario(ctx).token;

// ── Activos ──────────────────────────────────────────────────────────────────
const listarActivos = (ctx, nit) =>
  memo(ctx, `activos:${nit}`, () =>
    llamar('activos', RUTA_ACTIVOS, { query: { nitUniv: nit }, token: token(ctx) }),
  );

const obtenerActivo = (ctx, nit, placa) =>
  memo(ctx, `activo:${nit}:${placa}`, () =>
    llamar('activos', `${RUTA_ACTIVOS}/${encodeURIComponent(nit)}/${encodeURIComponent(placa)}`, {
      token: token(ctx),
    }),
  );

const invalidarActivos = (ctx) => {
  for (const k of ctx.cache?.keys() ?? []) if (k.startsWith('activo')) ctx.cache.delete(k);
};

// ── Prestamos ────────────────────────────────────────────────────────────────
const listarPrestamos = (ctx, nit, filtros = {}) =>
  llamar('prestamos', RUTA_PRESTAMOS, {
    query: { nitUniv: nit, placaInventario: filtros.placaInventario, estado: filtros.estado },
    token: token(ctx),
  });

// ── Notificaciones ───────────────────────────────────────────────────────────
const listarNotificaciones = (usuarioToken, { page = 0, size = 20, soloNoLeidas = false } = {}) =>
  llamar('notificaciones', RUTA_NOTIFICACIONES, {
    query: { page, size, soloNoLeidas },
    token: usuarioToken,
  });

const DateTime = new GraphQLScalarType({
  name: 'DateTime',
  description: 'Fecha y hora ISO-8601',
  serialize: (v) => (v == null ? v : String(v)),
  parseValue: (v) => {
    if (typeof v !== 'string' || Number.isNaN(Date.parse(v))) {
      throw new GraphQLError('DateTime invalido: use ISO-8601 (2026-05-01T08:00:00).');
    }
    return v;
  },
  parseLiteral: (ast) => {
    if (ast.kind !== Kind.STRING || Number.isNaN(Date.parse(ast.value))) {
      throw new GraphQLError('DateTime invalido: use ISO-8601 (2026-05-01T08:00:00).');
    }
    return ast.value;
  },
});

/**
 * Iterador asincrono cancelable. Un `async function*` no sirve aqui: su
 * return() queda en cola hasta el siguiente yield, y si el cliente se va
 * mientras se espera, el polling nunca termina.
 */
function vigilarNotificaciones(jwt) {
  const vistas = new Set();
  const cola = [];
  let primera = true;
  let cerrado = false;
  let despertar = null;
  let temporizador = null;

  const encolar = (item) => {
    cola.push(item);
    despertar?.();
  };

  const sondear = async () => {
    if (cerrado) return;
    try {
      const { items } = await listarNotificaciones(jwt, { size: 50, soloNoLeidas: true });
      for (const n of [...items].reverse()) {
        if (vistas.has(n.idNotificacion)) continue;
        vistas.add(n.idNotificacion);
        // La primera pasada solo toma la linea base: lo anterior ya lo cubre la query.
        if (!primera) encolar({ notificacionRecibida: n });
      }
      primera = false;
    } catch (err) {
      // Token vencido: cerrar es mejor que reintentar para siempre.
      if (err.extensions?.code === 'UNAUTHENTICATED' || err.extensions?.code === 'FORBIDDEN') {
        encolar({ error: err });
        return;
      }
    }
    if (!cerrado) temporizador = setTimeout(sondear, config.pollNotificacionesMs);
  };
  sondear();

  const cerrar = () => {
    cerrado = true;
    clearTimeout(temporizador);
    despertar?.();
  };

  return {
    [Symbol.asyncIterator]() {
      return this;
    },
    async next() {
      while (!cola.length && !cerrado) await new Promise((r) => (despertar = r));
      const item = cola.shift();
      if (!item) return { value: undefined, done: true };
      if (item.error) {
        cerrar();
        throw item.error;
      }
      return { value: item, done: false };
    },
    async return() {
      cerrar();
      return { value: undefined, done: true };
    },
    async throw(err) {
      cerrar();
      throw err;
    },
  };
}

export const resolvers = {
  DateTime,

  Query: {
    me: (_, __, ctx) => {
      const u = exigirUsuario(ctx);
      return { email: u.email, nitUniv: u.nitUniv, roles: u.roles };
    },

    // Una sola consulta del frontend -> varios servicios en paralelo.
    resumen: async (_, __, ctx) => {
      const nit = resolverNit(ctx);
      const [activos, vigentes, pagina] = await Promise.all([
        listarActivos(ctx, nit),
        listarPrestamos(ctx, nit, { estado: 'VIGENTE' }),
        listarNotificaciones(token(ctx), { size: 1 }),
      ]);
      const porEstado = new Map();
      for (const a of activos) porEstado.set(a.estado, (porEstado.get(a.estado) ?? 0) + 1);
      return {
        totalActivos: activos.length,
        activosPorEstado: [...porEstado].map(([estado, total]) => ({ estado, total })),
        prestamosVigentes: vigentes.length,
        prestamosVencidos: vigentes.filter((p) => p.vencido).length,
        notificacionesNoLeidas: pagina.noLeidas,
      };
    },

    activos: async (_, { estado, codSede }, ctx) => {
      const lista = await listarActivos(ctx, resolverNit(ctx));
      return lista.filter((a) => (!estado || a.estado === estado) && (!codSede || a.codSede === codSede));
    },

    activo: async (_, { placaInventario }, ctx) => {
      try {
        return await obtenerActivo(ctx, resolverNit(ctx), placaInventario);
      } catch (err) {
        if (err.extensions?.code === 'NOT_FOUND') return null;
        throw err;
      }
    },

    asignaciones: (_, { placaInventario, numDocumento }, ctx) =>
      llamar('inventario', RUTA_ASIGNACIONES, {
        query: { nitUniv: resolverNit(ctx), placaInventario, numDocumento },
        token: token(ctx),
      }),

    movimientos: (_, { placaInventario, tipo }, ctx) =>
      llamar('inventario', RUTA_MOVIMIENTOS, {
        query: { nitUniv: resolverNit(ctx), placaInventario, tipoMovimiento: tipo },
        token: token(ctx),
      }),

    prestamos: async (_, { placaInventario, estado, soloVencidos }, ctx) => {
      const lista = await listarPrestamos(ctx, resolverNit(ctx), { placaInventario, estado });
      return soloVencidos ? lista.filter((p) => p.vencido) : lista;
    },

    notificaciones: (_, args, ctx) => listarNotificaciones(token(ctx), args),

    notificacionesNoLeidas: async (_, __, ctx) =>
      (await listarNotificaciones(token(ctx), { size: 1 })).noLeidas,
  },

  Mutation: {
    crearActivo: async (_, { input }, ctx) => {
      const creado = await llamar('activos', RUTA_ACTIVOS, {
        metodo: 'POST',
        cuerpo: { ...input, nitUniv: resolverNit(ctx) },
        token: token(ctx),
      });
      invalidarActivos(ctx);
      return creado;
    },

    actualizarActivo: async (_, { placaInventario, input }, ctx) => {
      const nit = resolverNit(ctx);
      const actualizado = await llamar(
        'activos',
        `${RUTA_ACTIVOS}/${encodeURIComponent(nit)}/${encodeURIComponent(placaInventario)}`,
        { metodo: 'PUT', cuerpo: input, token: token(ctx) },
      );
      invalidarActivos(ctx);
      return actualizado;
    },

    darDeBajaActivo: async (_, { placaInventario }, ctx) => {
      const nit = resolverNit(ctx);
      await llamar(
        'activos',
        `${RUTA_ACTIVOS}/${encodeURIComponent(nit)}/${encodeURIComponent(placaInventario)}`,
        { metodo: 'DELETE', token: token(ctx) },
      );
      invalidarActivos(ctx);
      return obtenerActivo(ctx, nit, placaInventario);
    },

    crearAsignacion: (_, { input }, ctx) =>
      llamar('inventario', RUTA_ASIGNACIONES, {
        metodo: 'POST',
        cuerpo: { ...input, nitUniv: resolverNit(ctx) },
        token: token(ctx),
      }),

    registrarMovimiento: (_, { input: { idempotencyKey, ...datos } }, ctx) =>
      llamar('inventario', RUTA_MOVIMIENTOS, {
        metodo: 'POST',
        cuerpo: { ...datos, nitUniv: resolverNit(ctx) },
        headers: idempotencyKey ? { 'Idempotency-Key': idempotencyKey } : undefined,
        token: token(ctx),
      }),

    crearPrestamo: (_, { input }, ctx) =>
      llamar('prestamos', RUTA_PRESTAMOS, {
        metodo: 'POST',
        cuerpo: { ...input, nitUniv: resolverNit(ctx) },
        token: token(ctx),
      }),

    registrarDevolucion: (_, { idPrestamo, fechaDevolucionReal }, ctx) =>
      llamar('prestamos', `${RUTA_PRESTAMOS}/${encodeURIComponent(idPrestamo)}/devolucion`, {
        metodo: 'PATCH',
        cuerpo: { fechaDevolucionReal },
        token: token(ctx),
      }),

    marcarNotificacionLeida: (_, { idNotificacion }, ctx) =>
      llamar('notificaciones', `${RUTA_NOTIFICACIONES}/${encodeURIComponent(idNotificacion)}/leida`, {
        metodo: 'PATCH',
        token: token(ctx),
      }),

    marcarTodasNotificacionesLeidas: async (_, __, ctx) =>
      (await llamar('notificaciones', `${RUTA_NOTIFICACIONES}/leidas`, { metodo: 'PATCH', token: token(ctx) }))
        .noLeidas,
  },

  Subscription: {
    notificacionRecibida: {
      /**
       * El gateway revisa el servicio de notificaciones cada
       * ETER_POLL_NOTIFICACIONES_MS y empuja por el WebSocket solo las nuevas.
       * Si el servicio de notificaciones algun dia publica eventos, basta con
       * cambiar este iterador por un consumidor de ese canal.
       */
      subscribe: (_, __, ctx) => vigilarNotificaciones(token(ctx)),
    },
  },

  ActivoFijo: {
    asignaciones: (a, _, ctx) =>
      llamar('inventario', RUTA_ASIGNACIONES, {
        query: { nitUniv: a.nitUniv, placaInventario: a.placaInventario },
        token: token(ctx),
      }),
    movimientos: (a, { tipo }, ctx) =>
      llamar('inventario', RUTA_MOVIMIENTOS, {
        query: { nitUniv: a.nitUniv, placaInventario: a.placaInventario, tipoMovimiento: tipo },
        token: token(ctx),
      }),
    prestamos: (a, { estado }, ctx) =>
      listarPrestamos(ctx, a.nitUniv, { placaInventario: a.placaInventario, estado }),
    prestamoVigente: async (a, _, ctx) =>
      (await listarPrestamos(ctx, a.nitUniv, { placaInventario: a.placaInventario, estado: 'VIGENTE' }))[0] ??
      null,
  },

  Asignacion: { activo: (r, _, ctx) => activoRelacionado(r, ctx) },
  Movimiento: {
    idMovimiento: (m) => String(m.idMovimiento),
    activo: (r, _, ctx) => activoRelacionado(r, ctx),
  },
  Prestamo: { activo: (r, _, ctx) => activoRelacionado(r, ctx) },
  Notificacion: {
    activo: (n, _, ctx) => (n.placaInventario ? activoRelacionado(n, ctx) : null),
  },
};

async function activoRelacionado(r, ctx) {
  try {
    return await obtenerActivo(ctx, r.nitUniv, r.placaInventario);
  } catch (err) {
    // Un activo borrado no debe tumbar toda la lista.
    if (err.extensions?.code === 'NOT_FOUND') return null;
    throw err;
  }
}
