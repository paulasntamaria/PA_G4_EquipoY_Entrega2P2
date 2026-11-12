// Cliente del gateway GraphQL (ETER.sesion y ETER.api), con modo demostración.
(function () {
  'use strict';
  const ETER = (window.ETER = window.ETER || {});

  const almacen = {
    leer(k) { try { return localStorage.getItem(k); } catch { return null; } },
    escribir(k, v) { try { localStorage.setItem(k, v); } catch { /* sin almacenamiento */ } },
    borrar(k) { try { localStorage.removeItem(k); } catch { /* idem */ } },
  };
  const CLAVES = { gateway: 'eter.gateway', token: 'eter.token', demo: 'eter.demo', datos: 'eter.demo.datos' };
  const GATEWAY_POR_DEFECTO = 'http://127.0.0.1:4000/graphql';
  /**
   * localhost -> 127.0.0.1. En Windows, "localhost" resuelve primero a IPv6 (::1) y el reenvío de
   * puertos de Docker Desktop por IPv6 a veces acepta la conexión y no responde: el navegador queda
   * colgado sin probar IPv4. Es la misma máquina, así que se usa IPv4 directamente.
   */
  const normalizarGateway = (url) => String(url || '').trim().replace(/^(\w+:\/\/)localhost(?=[:/]|$)/i, '$1127.0.0.1');

  // Errores
  class ApiError extends Error {
    constructor(code, message, extra = {}) {
      super(message);
      this.name = 'ApiError';
      this.code = code;
      this.servicio = extra.servicio;
      this.status = extra.status;
      this.errores = Array.isArray(extra.errores) ? extra.errores : [];
    }
  }
  const desdeGraphQL = (e) => new ApiError(e.extensions?.code || 'ERROR', e.message, e.extensions || {});

  // Sesión
  function leerJwt(token) {
    try {
      const b64 = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
      const bytes = Uint8Array.from(atob(b64.padEnd(Math.ceil(b64.length / 4) * 4, '=')), (c) => c.charCodeAt(0));
      return JSON.parse(new TextDecoder().decode(bytes));
    } catch {
      return null;
    }
  }

  const USUARIO_DEMO = { email: '79123456@eter.edu.co', nitUniv: '900123456-1', roles: ['ADMIN'] };

  const sesion = {
    get gateway() { return normalizarGateway(almacen.leer(CLAVES.gateway)) || GATEWAY_POR_DEFECTO; },
    get token() { return almacen.leer(CLAVES.token); },
    get demo() { return almacen.leer(CLAVES.demo) === '1'; },
    activa() { return this.demo || !!this.token; },
    /** Datos del JWT (la firma la verifica el gateway). */
    usuario() {
      if (this.demo) return { ...USUARIO_DEMO };
      const c = this.token && leerJwt(this.token);
      if (!c) return null;
      return { email: c.sub, nitUniv: c.nitUniv, roles: Array.isArray(c.roles) ? c.roles : [], exp: c.exp };
    },
    esAdmin() {
      const roles = this.usuario()?.roles ?? [];
      return roles.includes('ADMIN') || roles.includes('ROLE_ADMIN');
    },
    vencida() {
      const u = this.usuario();
      return !!(u && u.exp && u.exp * 1000 < Date.now());
    },
    /** Claims del token o null. No verifica la firma. */
    claims(token) {
      return leerJwt(String(token || '').trim().replace(/^Bearer\s+/i, ''));
    },
    /** true si parece un JWT de ETER. */
    tokenValido(token) {
      const c = this.claims(token);
      return !!(c && c.sub && c.nitUniv);
    },
    iniciar(token, gateway) {
      almacen.borrar(CLAVES.demo);
      almacen.escribir(CLAVES.token, String(token).trim().replace(/^Bearer\s+/i, ''));
      if (gateway && gateway.trim()) almacen.escribir(CLAVES.gateway, normalizarGateway(gateway));
    },
    iniciarDemo() {
      almacen.borrar(CLAVES.token);
      almacen.escribir(CLAVES.demo, '1');
    },
    cerrar() {
      almacen.borrar(CLAVES.token);
      almacen.borrar(CLAVES.demo);
    },
  };

  // Fechas (LocalDateTime sin zona)
  const dos = (n) => String(n).padStart(2, '0');
  /** Date -> "2026-10-28T08:00:00" en hora local. */
  function isoLocal(fecha = new Date()) {
    return `${fecha.getFullYear()}-${dos(fecha.getMonth() + 1)}-${dos(fecha.getDate())}T${dos(fecha.getHours())}:${dos(fecha.getMinutes())}:${dos(fecha.getSeconds())}`;
  }
  function uuid() {
    if (crypto.randomUUID) return crypto.randomUUID();
    const b = crypto.getRandomValues(new Uint8Array(16));
    b[6] = (b[6] & 0x0f) | 0x40; b[8] = (b[8] & 0x3f) | 0x80;
    const h = [...b].map((x) => x.toString(16).padStart(2, '0')).join('');
    return `${h.slice(0, 8)}-${h.slice(8, 12)}-${h.slice(12, 16)}-${h.slice(16, 20)}-${h.slice(20)}`;
  }
  const sinVacios = (o) => Object.fromEntries(Object.entries(o || {}).filter(([, v]) => v !== undefined && v !== null && v !== ''));

  // Modo real
  async function gql(query, variables) {
    let r;
    try {
      r = await fetch(sesion.gateway, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Accept: 'application/json',
          ...(sesion.token ? { Authorization: `Bearer ${sesion.token}` } : {}),
        },
        body: JSON.stringify({ query, variables }),
      });
    } catch {
      throw new ApiError('SIN_CONEXION', `No hay respuesta del gateway en ${sesion.gateway}. Revisa que esté corriendo (docker compose up o gradlew bootRun).`);
    }
    let cuerpo = null;
    try { cuerpo = await r.json(); } catch { /* cuerpo no JSON */ }
    if (!cuerpo) throw new ApiError('RESPUESTA_INVALIDA', `El gateway respondió ${r.status} sin un cuerpo JSON.`);
    const errores = cuerpo.errors || [];
    const datos = cuerpo.data;
    const sinDatos = !datos || Object.values(datos).every((v) => v == null);
    if (errores.length && sinDatos) throw desdeGraphQL(errores[0]);
    // Datos parciales: se avisa y se sigue.
    if (errores.length) window.dispatchEvent(new CustomEvent('eter:avisos', { detail: errores.map(desdeGraphQL) }));
    return datos;
  }

  const F = {
    activo: 'nitUniv placaInventario descripcion codUnspsc codSede valorContable estado',
    prestamo: 'idPrestamo nitUniv placaInventario numDocumento fechaSalida fechaDevolucionPactada fechaDevolucionReal estado vencido',
    movimiento: 'idMovimiento nitUniv placaInventario tipoMovimiento fechaMovimiento numDocumento',
    asignacion: 'nitUniv placaInventario numDocumento fechaHoraActa',
    notificacion: 'idNotificacion eventId tipo titulo mensaje leida creadaEn leidaEn placaInventario destinatarioEmail',
  };

  const real = {
    me: async () => (await gql('query { me { email nitUniv roles } }')).me,

    resumen: async () => (await gql(
      'query { resumen { totalActivos prestamosVigentes prestamosVencidos notificacionesNoLeidas activosPorEstado { estado total } } }',
    )).resumen,

    activos: async (f = {}) => (await gql(
      `query ($estado: EstadoActivo, $codSede: String) { activos(estado: $estado, codSede: $codSede) { ${F.activo} } }`,
      sinVacios({ estado: f.estado, codSede: f.codSede }),
    )).activos,

    activo: async (placa) => (await gql(
      `query ($p: String!) { activo(placaInventario: $p) {
        ${F.activo}
        prestamoVigente { ${F.prestamo} }
        prestamos { ${F.prestamo} }
        movimientos { ${F.movimiento} }
        asignaciones { ${F.asignacion} }
      } }`,
      { p: placa },
    )).activo,

    asignaciones: async (f = {}) => (await gql(
      `query ($p: String, $d: String) { asignaciones(placaInventario: $p, numDocumento: $d) { ${F.asignacion} activo { descripcion estado codSede } } }`,
      sinVacios({ p: f.placaInventario, d: f.numDocumento }),
    )).asignaciones,

    movimientos: async (f = {}) => (await gql(
      `query ($p: String, $t: TipoMovimiento) { movimientos(placaInventario: $p, tipo: $t) { ${F.movimiento} activo { descripcion estado } } }`,
      sinVacios({ p: f.placaInventario, t: f.tipo }),
    )).movimientos,

    prestamos: async (f = {}) => (await gql(
      `query ($p: String, $e: EstadoPrestamo, $v: Boolean) { prestamos(placaInventario: $p, estado: $e, soloVencidos: $v) { ${F.prestamo} activo { descripcion codSede estado } } }`,
      sinVacios({ p: f.placaInventario, e: f.estado, v: f.soloVencidos || undefined }),
    )).prestamos,

    notificaciones: async (f = {}) => (await gql(
      `query ($page: Int, $size: Int, $s: Boolean) { notificaciones(page: $page, size: $size, soloNoLeidas: $s) { items { ${F.notificacion} } page size totalElements noLeidas } }`,
      { page: f.page ?? 0, size: f.size ?? 20, s: !!f.soloNoLeidas },
    )).notificaciones,

    notificacionesNoLeidas: async () => (await gql('query { notificacionesNoLeidas }')).notificacionesNoLeidas,

    crearActivo: async (input) => (await gql(
      `mutation ($i: CrearActivoInput!) { crearActivo(input: $i) { ${F.activo} } }`, { i: input },
    )).crearActivo,

    actualizarActivo: async (placa, input) => (await gql(
      `mutation ($p: String!, $i: ActualizarActivoInput!) { actualizarActivo(placaInventario: $p, input: $i) { ${F.activo} } }`,
      { p: placa, i: input },
    )).actualizarActivo,

    darDeBajaActivo: async (placa) => (await gql(
      `mutation ($p: String!) { darDeBajaActivo(placaInventario: $p) { ${F.activo} } }`, { p: placa },
    )).darDeBajaActivo,

    crearAsignacion: async (input) => (await gql(
      `mutation ($i: CrearAsignacionInput!) { crearAsignacion(input: $i) { ${F.asignacion} } }`, { i: input },
    )).crearAsignacion,

    registrarMovimiento: async (input) => (await gql(
      `mutation ($i: RegistrarMovimientoInput!) { registrarMovimiento(input: $i) { ${F.movimiento} } }`, { i: input },
    )).registrarMovimiento,

    crearPrestamo: async (input) => (await gql(
      `mutation ($i: CrearPrestamoInput!) { crearPrestamo(input: $i) { ${F.prestamo} activo { descripcion } } }`, { i: input },
    )).crearPrestamo,

    registrarDevolucion: async (idPrestamo, fechaDevolucionReal) => (await gql(
      `mutation ($id: ID!, $f: DateTime!) { registrarDevolucion(idPrestamo: $id, fechaDevolucionReal: $f) { ${F.prestamo} } }`,
      { id: idPrestamo, f: fechaDevolucionReal },
    )).registrarDevolucion,

    marcarNotificacionLeida: async (id) => (await gql(
      `mutation ($id: ID!) { marcarNotificacionLeida(idNotificacion: $id) { ${F.notificacion} } }`, { id },
    )).marcarNotificacionLeida,

    marcarTodasNotificacionesLeidas: async () => (await gql('mutation { marcarTodasNotificacionesLeidas }')).marcarTodasNotificacionesLeidas,

    /** Suscripción por WebSocket. Devuelve una función para cerrarla. */
    suscribirNotificaciones({ onNext, onError, onEstado } = {}) {
      const url = sesion.gateway.replace(/^http/i, 'ws');
      const query = `subscription { notificacionRecibida { ${F.notificacion} } }`;
      let ws = null, cerrada = false, intentos = 0, temporizador = null;

      const abrir = () => {
        onEstado?.(intentos ? 'reconectando' : 'conectando');
        try { ws = new WebSocket(url, 'graphql-transport-ws'); } catch (e) { onError?.(new ApiError('SIN_CONEXION', e.message)); return; }
        ws.onopen = () => ws.send(JSON.stringify({ type: 'connection_init', payload: { Authorization: `Bearer ${sesion.token}` } }));
        ws.onmessage = (ev) => {
          let m;
          try { m = JSON.parse(ev.data); } catch { return; }
          if (m.type === 'connection_ack') {
            intentos = 0;
            onEstado?.('en-vivo');
            ws.send(JSON.stringify({ id: '1', type: 'subscribe', payload: { query } }));
          } else if (m.type === 'next') {
            if (m.payload?.errors?.length) onError?.(desdeGraphQL(m.payload.errors[0]));
            else if (m.payload?.data?.notificacionRecibida) onNext?.(m.payload.data.notificacionRecibida);
          } else if (m.type === 'error') {
            onError?.(desdeGraphQL((m.payload || [])[0] || { message: 'Error en la suscripción.' }));
          } else if (m.type === 'ping') {
            ws.send(JSON.stringify({ type: 'pong' }));
          } else if (m.type === 'complete') {
            cerrada = true;
            onEstado?.('cerrada');
          }
        };
        ws.onclose = () => {
          if (cerrada) return;
          onEstado?.('reconectando');
          temporizador = setTimeout(abrir, Math.min(30000, 1000 * 2 ** intentos++));
        };
      };
      abrir();
      return () => {
        cerrada = true;
        clearTimeout(temporizador);
        try { ws && ws.readyState === 1 && ws.send(JSON.stringify({ id: '1', type: 'complete' })); } catch { /* ya cerrado */ }
        try { ws && ws.close(); } catch { /* ya cerrado */ }
        onEstado?.('cerrada');
      };
    },
  };

  // Modo demostración
  const demo = (() => {
    const NIT = USUARIO_DEMO.nitUniv;
    const EMAIL = USUARIO_DEMO.email;
    const RX = {
      codigo: /^[A-Za-z0-9]+(-[A-Za-z0-9]+)*$/,
      unspsc: /^[0-9]{8}$/,
      documento: /^[A-Za-z0-9]{5,20}$/,
    };
    const ESTADOS_ACTIVO = ['ACTIVO', 'MANTENIMIENTO', 'BAJA'];
    const TIPOS_MOV = ['ALTA', 'BAJA', 'TRASLADO', 'MANTENIMIENTO', 'ASIGNACION', 'PRESTAMO', 'DEVOLUCION'];
    let db = null;

    const pausa = (ms = 180 + Math.random() * 260) => new Promise((r) => setTimeout(r, ms));
    const copia = (x) => JSON.parse(JSON.stringify(x));
    const guardar = () => almacen.escribir(CLAVES.datos, JSON.stringify(db));
    function cargar(forzar = false) {
      if (db && !forzar) return db;
      try { db = JSON.parse(almacen.leer(CLAVES.datos)); } catch { db = null; }
      if (!db || db.version !== 1) { db = sembrar(); guardar(); }
      return db;
    }
    const ahora = () => isoLocal(new Date());
    const vencido = (p) => p.estado === 'VIGENTE' && p.fechaDevolucionPactada < ahora();
    const conVencido = (p) => ({ ...p, vencido: vencido(p) });
    const error = (code, message, errores) => new ApiError(code, message, { errores, servicio: 'demostración' });
    const validar = (errores) => {
      if (errores.length) {
        throw error('BAD_USER_INPUT', `Solicitud inválida: ${errores.map((e) => `${e.campo}: ${e.mensaje}`).join('; ')}`, errores);
      }
    };
    const exigirAdmin = () => { if (!sesion.esAdmin()) throw error('FORBIDDEN', 'Su rol no tiene permiso para realizar esta operación.'); };
    const buscarActivo = (placa) => cargar().activos.find((a) => a.placaInventario === placa);
    const exigirActivo = (placa) => {
      const a = buscarActivo(placa);
      if (!a) throw error('NOT_FOUND', `No existe el activo ${placa} en la universidad ${NIT}.`);
      return a;
    };
    const resumirActivo = (placa) => {
      const a = buscarActivo(placa);
      return a ? { descripcion: a.descripcion, estado: a.estado, codSede: a.codSede } : null;
    };

    function sembrar() {
      const d = (dias, hora = 9, min = 0) => {
        const x = new Date();
        x.setDate(x.getDate() + dias);
        x.setHours(hora, min, 0, 0);
        return isoLocal(x);
      };
      const activos = [
        ['AF-2026-0001', 'Portátil Dell Latitude 5440', '43211503', 'SED-01', 4890000, 'ACTIVO'],
        ['AF-2026-0002', 'Videobeam Epson PowerLite E20', '45111609', 'SED-01', 2350000, 'ACTIVO'],
        ['AF-2026-0003', 'Cámara Canon EOS R10', '45121504', 'SED-02', 5120000, 'ACTIVO'],
        ['AF-2026-0004', 'Osciloscopio Rigol DS1054Z', '41113801', 'SED-02', 2780000, 'ACTIVO'],
        ['AF-2026-0005', 'Computador de escritorio HP ProDesk 400', '43211507', 'SED-02', 3200000, 'MANTENIMIENTO'],
        ['AF-2026-0006', 'Tableta Samsung Galaxy Tab S9', '43211509', 'SED-01', 3650000, 'ACTIVO'],
        ['AF-2026-0007', 'Impresora HP LaserJet M404', '43212105', 'SED-03', 1480000, 'ACTIVO'],
        ['AF-2026-0008', 'Televisor LG 65 pulgadas', '52161505', 'SED-03', 3900000, 'ACTIVO'],
        ['AF-2026-0009', 'Microscopio Olympus CX23', '41111724', 'SED-02', 6700000, 'ACTIVO'],
        ['AF-2026-0010', 'Balanza analítica Ohaus PX224', '41111604', 'SED-02', 4150000, 'MANTENIMIENTO'],
        ['AF-2026-0011', 'Cámara de seguridad Hikvision', '46171610', 'SED-01', 890000, 'BAJA'],
        ['AF-2026-0012', 'Portátil Lenovo ThinkPad T14', '43211503', 'SED-03', 5300000, 'ACTIVO'],
      ].map(([placaInventario, descripcion, codUnspsc, codSede, valorContable, estado]) =>
        ({ nitUniv: NIT, placaInventario, descripcion, codUnspsc, codSede, valorContable, estado }));

      const prestamos = [
        ['AF-2026-0001', '1012345678', d(-40, 8), d(-33, 18), d(-34, 16), 'DEVUELTO'],
        ['AF-2026-0003', '52987654', d(-6, 10), d(8, 18), null, 'VIGENTE'],
        ['AF-2026-0001', '79123456', d(-3, 8), d(7, 18), null, 'VIGENTE'],
        ['AF-2026-0004', '79123456', d(-12, 14), d(-1, 18), null, 'VIGENTE'],
        ['AF-2026-0003', '1012345678', d(-25, 9), d(-18, 18), d(-18, 11), 'DEVUELTO'],
        ['AF-2026-0002', '79123456', d(-15, 7, 30), d(-14, 18), d(-14, 17, 10), 'DEVUELTO'],
      ].map(([placaInventario, numDocumento, fechaSalida, fechaDevolucionPactada, fechaDevolucionReal, estado]) =>
        ({ idPrestamo: uuid(), nitUniv: NIT, placaInventario, numDocumento, fechaSalida, fechaDevolucionPactada, fechaDevolucionReal, estado }));

      const asignaciones = [
        ['AF-2026-0006', '79123456', d(-50, 10)],
        ['AF-2026-0012', '52987654', d(-45, 11, 30)],
        ['AF-2026-0009', '1020304050', d(-70, 9)],
        ['AF-2026-0007', '1012345678', d(-35, 15)],
      ].map(([placaInventario, numDocumento, fechaHoraActa]) => ({ nitUniv: NIT, placaInventario, numDocumento, fechaHoraActa }));

      let n = 0;
      const mov = (placaInventario, tipoMovimiento, fechaMovimiento, numDocumento) =>
        ({ idMovimiento: String(++n), nitUniv: NIT, placaInventario, tipoMovimiento, fechaMovimiento, numDocumento });
      const movimientos = [];
      activos.forEach((a, i) => movimientos.push(mov(a.placaInventario, 'ALTA', d(-120 + i * 4, 10), '79123456')));
      asignaciones.forEach((a) => movimientos.push(mov(a.placaInventario, 'ASIGNACION', a.fechaHoraActa, a.numDocumento)));
      movimientos.push(mov('AF-2026-0008', 'TRASLADO', d(-30, 11), '79123456'));
      movimientos.push(mov('AF-2026-0011', 'BAJA', d(-20, 16), '79123456'));
      movimientos.push(mov('AF-2026-0010', 'MANTENIMIENTO', d(-9, 8, 30), '1020304050'));
      movimientos.push(mov('AF-2026-0005', 'MANTENIMIENTO', d(-5, 14), '52987654'));
      prestamos.forEach((p) => {
        movimientos.push(mov(p.placaInventario, 'PRESTAMO', p.fechaSalida, p.numDocumento));
        if (p.fechaDevolucionReal) movimientos.push(mov(p.placaInventario, 'DEVOLUCION', p.fechaDevolucionReal, p.numDocumento));
      });
      movimientos.sort((a, b) => a.fechaMovimiento.localeCompare(b.fechaMovimiento));
      movimientos.forEach((m, i) => { m.idMovimiento = String(i + 1); });

      const notificaciones = [];
      prestamos.filter((p) => p.numDocumento === '79123456').forEach((p) => {
        notificaciones.push(notif('PRESTAMO_CREADO', p, p.fechaSalida, true));
        if (p.fechaDevolucionReal) notificaciones.push(notif('PRESTAMO_DEVUELTO', p, p.fechaDevolucionReal, true));
        // El vigia lo detecta poco despues de la fecha pactada.
        if (vencido(p)) notificaciones.push(notif('PRESTAMO_VENCIDO', p, d(-1, 18, 1), false));
      });
      // La última queda sin leer.
      notificaciones.sort((a, b) => a.creadaEn.localeCompare(b.creadaEn));
      const reciente = notificaciones.filter((x) => x.tipo === 'PRESTAMO_CREADO').at(-1);
      if (reciente) { reciente.leida = false; reciente.leidaEn = null; }

      return { version: 1, activos, prestamos, asignaciones, movimientos, notificaciones, sigMov: movimientos.length + 1, pendientes: [] };
    }

    // Mismos textos que NotificacionEventConsumer.
    function notif(tipo, p, creadaEn, leida) {
      const fecha = p.fechaDevolucionPactada.slice(0, 16);
      const placa = p.placaInventario;
      const titulo = { PRESTAMO_CREADO: `Préstamo registrado: ${placa}`, PRESTAMO_VENCIDO: `Préstamo vencido: ${placa}`, PRESTAMO_DEVUELTO: `Préstamo devuelto: ${placa}` }[tipo];
      const mensaje = {
        PRESTAMO_CREADO: `Se registró a su nombre el préstamo del activo ${placa}. Debe devolverlo a más tardar el ${fecha}.`,
        PRESTAMO_VENCIDO: `El activo ${placa} no fue devuelto en la fecha pactada (${fecha}). Por favor regularice la devolución.`,
        PRESTAMO_DEVUELTO: `Se registró la devolución del activo ${placa}.`,
      }[tipo];
      return {
        idNotificacion: uuid(), eventId: uuid(), tipo, titulo, mensaje,
        leida, creadaEn, leidaEn: leida ? creadaEn : null,
        placaInventario: placa, destinatarioEmail: `${p.numDocumento}@eter.edu.co`,
      };
    }

    // Simula los consumidores de Kafka.
    function encolarEvento(evento) {
      cargar().pendientes.push({ ...evento, listoEn: Date.now() + 2500 });
      guardar();
    }
    function procesarEventos() {
      const datos = cargar(true);
      const listos = datos.pendientes.filter((e) => e.listoEn <= Date.now());
      if (!listos.length) return;
      datos.pendientes = datos.pendientes.filter((e) => e.listoEn > Date.now());
      for (const e of listos) {
        const p = e.prestamo;
        const nuevoMov = (tipo, fecha, doc) => datos.movimientos.push({
          idMovimiento: String(datos.sigMov++), nitUniv: NIT, placaInventario: e.placa, tipoMovimiento: tipo, fechaMovimiento: fecha, numDocumento: doc,
        });
        if (e.tipo === 'ACTIVO_CREADO') nuevoMov('ALTA', e.fecha, e.doc);
        if (e.tipo === 'ACTIVO_DADO_DE_BAJA') nuevoMov('BAJA', e.fecha, e.doc);
        if (e.tipo === 'PRESTAMO_CREADO') nuevoMov('PRESTAMO', p.fechaSalida, p.numDocumento);
        if (e.tipo === 'PRESTAMO_DEVUELTO') nuevoMov('DEVOLUCION', p.fechaDevolucionReal, p.numDocumento);
        if (p && `${p.numDocumento}@eter.edu.co` === EMAIL) datos.notificaciones.push(notif(e.tipo, p, ahora(), false));
      }
      guardar();
    }

    const bandeja = () => cargar().notificaciones.filter((n) => n.destinatarioEmail === EMAIL);
    const docUsuario = () => EMAIL.split('@')[0];

    const api = {
      async me() { await pausa(80); return { ...USUARIO_DEMO }; },

      async resumen() {
        await pausa(); procesarEventos();
        const { activos, prestamos } = cargar();
        const porEstado = new Map();
        activos.forEach((a) => porEstado.set(a.estado, (porEstado.get(a.estado) || 0) + 1));
        const vigentes = prestamos.filter((p) => p.estado === 'VIGENTE');
        return {
          totalActivos: activos.length,
          activosPorEstado: [...porEstado].map(([estado, total]) => ({ estado, total })),
          prestamosVigentes: vigentes.length,
          prestamosVencidos: vigentes.filter(vencido).length,
          notificacionesNoLeidas: bandeja().filter((n) => !n.leida).length,
        };
      },

      async activos(f = {}) {
        await pausa(); procesarEventos();
        return copia(cargar().activos.filter((a) => (!f.estado || a.estado === f.estado) && (!f.codSede || a.codSede === f.codSede)));
      },

      async activo(placa) {
        await pausa(); procesarEventos();
        const a = buscarActivo(placa);
        if (!a) return null;
        const datos = cargar();
        const prestamos = datos.prestamos.filter((p) => p.placaInventario === placa).map(conVencido);
        return copia({
          ...a,
          prestamoVigente: prestamos.find((p) => p.estado === 'VIGENTE') || null,
          prestamos,
          movimientos: datos.movimientos.filter((m) => m.placaInventario === placa),
          asignaciones: datos.asignaciones.filter((x) => x.placaInventario === placa),
        });
      },

      async asignaciones(f = {}) {
        await pausa(); procesarEventos();
        return copia(cargar().asignaciones
          .filter((x) => (!f.placaInventario || x.placaInventario === f.placaInventario) && (!f.numDocumento || x.numDocumento === f.numDocumento))
          .map((x) => ({ ...x, activo: resumirActivo(x.placaInventario) })));
      },

      async movimientos(f = {}) {
        await pausa(); procesarEventos();
        return copia(cargar().movimientos
          .filter((m) => (!f.placaInventario || m.placaInventario === f.placaInventario) && (!f.tipo || m.tipoMovimiento === f.tipo))
          .map((m) => ({ ...m, activo: resumirActivo(m.placaInventario) })));
      },

      async prestamos(f = {}) {
        await pausa(); procesarEventos();
        return copia(cargar().prestamos
          .map(conVencido)
          .filter((p) => (!f.placaInventario || p.placaInventario === f.placaInventario) && (!f.estado || p.estado === f.estado) && (!f.soloVencidos || p.vencido))
          .map((p) => ({ ...p, activo: resumirActivo(p.placaInventario) })));
      },

      async notificaciones(f = {}) {
        await pausa(); procesarEventos();
        const page = f.page ?? 0, size = f.size ?? 20;
        const todas = bandeja().slice().sort((a, b) => b.creadaEn.localeCompare(a.creadaEn));
        const filtradas = f.soloNoLeidas ? todas.filter((n) => !n.leida) : todas;
        return copia({
          items: filtradas.slice(page * size, page * size + size),
          page, size, totalElements: filtradas.length,
          noLeidas: todas.filter((n) => !n.leida).length,
        });
      },

      async notificacionesNoLeidas() {
        await pausa(60); procesarEventos();
        return bandeja().filter((n) => !n.leida).length;
      },

      async crearActivo(input) {
        await pausa(); exigirAdmin();
        const e = [];
        if (!input.placaInventario || !RX.codigo.test(input.placaInventario)) e.push({ campo: 'placaInventario', mensaje: 'solo admite letras, números y guiones (ej. AF-2026-0001)' });
        if (!input.descripcion || input.descripcion.trim().length < 3) e.push({ campo: 'descripcion', mensaje: 'debe tener entre 3 y 255 caracteres' });
        if (!RX.unspsc.test(input.codUnspsc || '')) e.push({ campo: 'codUnspsc', mensaje: 'debe ser un código UNSPSC de 8 dígitos (ej. 43211503)' });
        if (!RX.codigo.test(input.codSede || '')) e.push({ campo: 'codSede', mensaje: 'solo admite letras, números y guiones (ej. SED-01)' });
        if (!(Number(input.valorContable) >= 0)) e.push({ campo: 'valorContable', mensaje: 'no puede ser negativo' });
        if (!ESTADOS_ACTIVO.includes(input.estado)) e.push({ campo: 'estado', mensaje: 'no es un estado válido' });
        validar(e);
        if (buscarActivo(input.placaInventario)) throw error('CONFLICT', `Ya existe un activo con placa ${input.placaInventario} en la universidad ${NIT}.`);
        const activo = { nitUniv: NIT, ...input, valorContable: Number(input.valorContable) };
        cargar().activos.push(activo);
        guardar();
        encolarEvento({ tipo: 'ACTIVO_CREADO', placa: activo.placaInventario, fecha: ahora(), doc: docUsuario() });
        return copia(activo);
      },

      async actualizarActivo(placa, input) {
        await pausa(); exigirAdmin();
        const a = exigirActivo(placa);
        const e = [];
        if (!input.descripcion || input.descripcion.trim().length < 3) e.push({ campo: 'descripcion', mensaje: 'debe tener entre 3 y 255 caracteres' });
        if (!RX.unspsc.test(input.codUnspsc || '')) e.push({ campo: 'codUnspsc', mensaje: 'debe ser un código UNSPSC de 8 dígitos (ej. 43211503)' });
        if (!RX.codigo.test(input.codSede || '')) e.push({ campo: 'codSede', mensaje: 'solo admite letras, números y guiones (ej. SED-01)' });
        if (!(Number(input.valorContable) >= 0)) e.push({ campo: 'valorContable', mensaje: 'no puede ser negativo' });
        if (!ESTADOS_ACTIVO.includes(input.estado)) e.push({ campo: 'estado', mensaje: 'no es un estado válido' });
        validar(e);
        Object.assign(a, input, { valorContable: Number(input.valorContable) });
        guardar();
        return copia(a);
      },

      async darDeBajaActivo(placa) {
        await pausa(); exigirAdmin();
        const a = exigirActivo(placa);
        if (a.estado !== 'BAJA') {
          a.estado = 'BAJA';
          guardar();
          encolarEvento({ tipo: 'ACTIVO_DADO_DE_BAJA', placa, fecha: ahora(), doc: docUsuario() });
        }
        return copia(a);
      },

      async crearAsignacion(input) {
        await pausa(); exigirAdmin();
        const e = [];
        if (!RX.documento.test(input.numDocumento || '')) e.push({ campo: 'numDocumento', mensaje: 'debe tener entre 5 y 20 letras o números, sin espacios ni símbolos' });
        if (!input.fechaHoraActa) e.push({ campo: 'fechaHoraActa', mensaje: 'es obligatorio' });
        validar(e);
        exigirActivo(input.placaInventario);
        const datos = cargar();
        if (datos.asignaciones.some((x) => x.placaInventario === input.placaInventario && x.numDocumento === input.numDocumento)) {
          throw error('CONFLICT', `El activo ${input.placaInventario} ya está asignado al documento ${input.numDocumento}.`);
        }
        const asignacion = { nitUniv: NIT, ...input };
        datos.asignaciones.push(asignacion);
        guardar();
        return copia(asignacion);
      },

      async registrarMovimiento(input) {
        await pausa(); exigirAdmin();
        const { idempotencyKey, ...datosMov } = input;
        const e = [];
        if (!TIPOS_MOV.includes(datosMov.tipoMovimiento)) e.push({ campo: 'tipoMovimiento', mensaje: 'no es un tipo válido' });
        if (!datosMov.fechaMovimiento) e.push({ campo: 'fechaMovimiento', mensaje: 'es obligatorio' });
        if (!RX.documento.test(datosMov.numDocumento || '')) e.push({ campo: 'numDocumento', mensaje: 'debe tener entre 5 y 20 letras o números, sin espacios ni símbolos' });
        validar(e);
        exigirActivo(datosMov.placaInventario);
        const datos = cargar();
        // Misma Idempotency-Key: se devuelve el movimiento ya registrado.
        const repetido = idempotencyKey && datos.movimientos.find((m) => m.idempotencyKey === idempotencyKey);
        if (repetido) { const { idempotencyKey: _k, ...m } = repetido; return copia(m); }
        const m = { idMovimiento: String(datos.sigMov++), nitUniv: NIT, ...datosMov, ...(idempotencyKey ? { idempotencyKey } : {}) };
        datos.movimientos.push(m);
        guardar();
        const { idempotencyKey: _k, ...salida } = m;
        return copia(salida);
      },

      async crearPrestamo(input) {
        await pausa(); exigirAdmin();
        const e = [];
        if (!RX.documento.test(input.numDocumento || '')) e.push({ campo: 'numDocumento', mensaje: 'debe tener entre 5 y 20 letras o números, sin espacios ni símbolos' });
        if (!input.fechaSalida) e.push({ campo: 'fechaSalida', mensaje: 'es obligatorio' });
        if (!input.fechaDevolucionPactada) e.push({ campo: 'fechaDevolucionPactada', mensaje: 'es obligatorio' });
        if (input.fechaSalida && input.fechaDevolucionPactada && input.fechaDevolucionPactada <= input.fechaSalida) {
          e.push({ campo: 'fechaDevolucionPactada', mensaje: 'debe ser posterior a la fecha de salida' });
        }
        validar(e);
        exigirActivo(input.placaInventario);
        const datos = cargar();
        if (datos.prestamos.some((p) => p.placaInventario === input.placaInventario && p.estado === 'VIGENTE')) {
          throw error('CONFLICT', `El activo ${input.placaInventario} ya tiene un préstamo vigente en la universidad ${NIT}.`);
        }
        const p = { idPrestamo: uuid(), nitUniv: NIT, ...input, fechaDevolucionReal: null, estado: 'VIGENTE' };
        datos.prestamos.push(p);
        guardar();
        encolarEvento({ tipo: 'PRESTAMO_CREADO', placa: p.placaInventario, prestamo: p });
        return copia({ ...conVencido(p), activo: resumirActivo(p.placaInventario) });
      },

      async registrarDevolucion(idPrestamo, fechaDevolucionReal) {
        await pausa(); exigirAdmin();
        if (!fechaDevolucionReal) validar([{ campo: 'fechaDevolucionReal', mensaje: 'es obligatorio' }]);
        if (fechaDevolucionReal > ahora()) validar([{ campo: 'fechaDevolucionReal', mensaje: 'debe ser una fecha pasada o presente' }]);
        const p = cargar().prestamos.find((x) => x.idPrestamo === idPrestamo);
        if (!p) throw error('NOT_FOUND', `No existe el préstamo ${idPrestamo}.`);
        if (p.estado !== 'VIGENTE') throw error('CONFLICT', `El préstamo ${idPrestamo} ya está en estado ${p.estado}.`);
        Object.assign(p, { estado: 'DEVUELTO', fechaDevolucionReal });
        guardar();
        encolarEvento({ tipo: 'PRESTAMO_DEVUELTO', placa: p.placaInventario, prestamo: p });
        return copia(conVencido(p));
      },

      // Como notificaciones-service: requiere ADMIN.
      async marcarNotificacionLeida(id) {
        await pausa(120); exigirAdmin();
        const n = bandeja().find((x) => x.idNotificacion === id);
        if (!n) throw error('NOT_FOUND', `No existe la notificación ${id}.`);
        if (!n.leida) { n.leida = true; n.leidaEn = ahora(); guardar(); }
        return copia(n);
      },

      async marcarTodasNotificacionesLeidas() {
        await pausa(160); exigirAdmin();
        bandeja().forEach((n) => { if (!n.leida) { n.leida = true; n.leidaEn = ahora(); } });
        guardar();
        return 0;
      },

      /** Polling, como el gateway. */
      suscribirNotificaciones({ onNext, onEstado } = {}) {
        onEstado?.('conectando');
        procesarEventos();
        const vistas = new Set(bandeja().map((n) => n.idNotificacion));
        let cerrada = false;
        const arranque = setTimeout(() => { if (!cerrada) onEstado?.('en-vivo'); }, 400);
        const intervalo = setInterval(() => {
          procesarEventos();
          for (const n of bandeja()) {
            if (vistas.has(n.idNotificacion)) continue;
            vistas.add(n.idNotificacion);
            if (!n.leida) onNext?.(copia(n));
          }
        }, 3000);
        return () => { cerrada = true; clearTimeout(arranque); clearInterval(intervalo); onEstado?.('cerrada'); };
      },

      reiniciarDatos() { db = sembrar(); guardar(); },
    };
    return api;
  })();

  // Elige el modo en cada llamada
  const api = new Proxy({}, {
    get(_, nombre) {
      const fuente = sesion.demo ? demo : real;
      const metodo = fuente[nombre];
      return typeof metodo === 'function' ? metodo.bind(fuente) : undefined;
    },
  });

  ETER.ApiError = ApiError;
  ETER.sesion = sesion;
  ETER.api = api;
  ETER.isoLocal = isoLocal;
})();
