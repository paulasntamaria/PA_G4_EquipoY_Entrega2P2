// Dashboard: cada bloque carga y falla por separado.
(function () {
  'use strict';
  if (!ETER.ui.montar({ pagina: 'panel' })) return;
  const { ui, api, sesion } = ETER;
  const $ = (id) => document.getElementById(id);
  const MAX_FILAS = 5;

  const fecha = (iso) => {
    const d = iso ? new Date(iso) : null;
    return d && !Number.isNaN(d.getTime()) ? d.getTime() : 0;
  };
  const plural = (n, uno, varios) => `${ui.fmt.numero(n)} ${n === 1 ? uno : varios}`;

  // Franja superior
  const usuario = sesion.usuario() || {};
  const hoy = new Intl.DateTimeFormat('es-CO', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' }).format(new Date());
  $('bajada').innerHTML = ui.html`NIT <span class="mono">${usuario.nitUniv || '—'}</span> · ${hoy}`;

  function estadoSistema(estado, texto) {
    $('estado-sistema').dataset.estado = estado;
    $('estado-texto').textContent = texto;
  }
  window.addEventListener('eter:avisos', () => estadoSistema('parcial', 'Servicios parciales'));

  ui.soloAdmin($('btn-activo'), $('btn-prestamo'));
  if (!sesion.esAdmin()) {
    const roles = (usuario.roles || []).join(', ');
    const nota = $('nota-rol');
    nota.textContent = `Registrar activos y préstamos requiere el rol ADMIN; tu token trae ${roles || 'ningún rol'}.`;
    nota.hidden = false;
  }

  // Anillos SVG
  const RADIO = 48;
  const VUELTA = 2 * Math.PI * RADIO;

  /** Tramos de un anillo: [{ fraccion, tono }]. */
  function tramos(partes) {
    const hueco = partes.length > 1 ? 2 : 0;
    let recorrido = 0;
    return partes.map(({ fraccion, tono }) => {
      const largo = fraccion * VUELTA;
      const trazo = ui.html`<circle class="anillo-tramo trazo-${tono}" cx="60" cy="60" r="${RADIO}"
        stroke-dasharray="${Math.max(largo - hueco, 0.5).toFixed(2)} ${VUELTA.toFixed(2)}"
        stroke-dashoffset="${(-recorrido).toFixed(2)}"></circle>`;
      recorrido += largo;
      return trazo;
    });
  }

  // Cifras
  const ESTADOS = [['ACTIVO', 'ok'], ['MANTENIMIENTO', 'aviso'], ['BAJA', 'neutro']];
  const conteo = (lista) => Object.fromEntries((lista || []).map((x) => [x.estado, x.total]));

  const metrica = ({ href, icono, tono = 'primario', etiqueta, valor, nota, alerta = false }) => ui.html`
    <a class="metrica metrica--${tono}${alerta ? ' metrica--alerta' : ''}" href="${href}">
      <span class="metrica-icono">${ui.icono(icono)}</span>
      <span class="metrica-valor">${ui.fmt.numero(valor)}</span>
      <span class="metrica-etiqueta">${etiqueta}</span>
      <span class="metrica-nota">${nota}</span>
    </a>`;

  /** Tarjeta: parte del registro en servicio. */
  function metricaServicio(enServicio, total) {
    const pct = total ? Math.round((enServicio / total) * 100) : null;
    return ui.html`
      <a class="metrica metrica--anillo" href="activos.html?estado=ACTIVO">
        <svg class="anillo anillo--metrica" viewBox="0 0 120 120" role="img" aria-label="${pct === null ? 'Sin activos registrados' : `${pct} % de los activos en servicio`}">
          <circle class="anillo-fondo" cx="60" cy="60" r="${RADIO}"></circle>
          ${pct ? tramos([{ fraccion: pct / 100, tono: 'primario' }]) : ''}
          <text class="anillo-valor" x="60" y="68" text-anchor="middle">${pct === null ? '—' : `${pct}%`}</text>
        </svg>
        <span class="metrica-etiqueta">En servicio</span>
      </a>`;
  }

  async function pintarCifras(resumen) {
    const zona = $('cifras-zona');
    const caja = $('cifras');
    caja.innerHTML = ui.html`${[0, 1, 2, 3, 4].map(() => ui.html`
      <div class="metrica" aria-hidden="true">
        <span class="esqueleto metrica-esqueleto-icono"></span>
        <span class="esqueleto metrica-esqueleto-valor"></span>
        <span class="esqueleto metrica-esqueleto-texto"></span>
      </div>`)}`;
    try {
      const r = await resumen;
      const porEstado = conteo(r.activosPorEstado);
      const vencidos = r.prestamosVencidos || 0;
      const vigentes = r.prestamosVigentes || 0;
      caja.innerHTML = ui.html`
        ${metrica({
          href: 'activos.html', icono: 'activos', etiqueta: 'Activos registrados', valor: r.totalActivos,
          nota: `${ui.fmt.numero(porEstado.ACTIVO || 0)} en servicio`,
        })}
        ${metrica({
          href: 'prestamos.html', icono: 'prestamos', tono: 'info', etiqueta: 'Préstamos vigentes', valor: vigentes,
          nota: vigentes ? `${ui.fmt.numero(Math.max(0, vigentes - vencidos))} dentro del plazo` : 'Ningún activo prestado',
        })}
        ${metrica({
          href: 'prestamos.html?filtro=vencidos', icono: 'alerta', tono: vencidos ? 'peligro' : 'ok', alerta: vencidos > 0,
          etiqueta: 'Vencidos', valor: vencidos,
          nota: vencidos ? 'Pasaron la fecha pactada' : 'Ninguno fuera de plazo',
        })}
        ${metrica({
          href: 'notificaciones.html', icono: 'notificaciones', tono: 'aviso', etiqueta: 'Sin leer', valor: r.notificacionesNoLeidas,
          nota: 'En tu bandeja',
        })}
        ${metricaServicio(porEstado.ACTIVO || 0, r.totalActivos || 0)}`;
      if ($('estado-sistema').dataset.estado === 'cargando') {
        estadoSistema('ok', sesion.demo ? 'Demostración activa' : 'Gateway en línea');
      }
    } catch (e) {
      estadoSistema('error', 'Sin conexión');
      ui.error(e, { en: zona });
    }
  }

  async function pintarEstados(resumen) {
    const caja = $('estados');
    caja.innerHTML = ui.html`<div class="hoja-cuerpo">${ui.cargandoBloque(4)}</div>`;
    try {
      const r = await resumen;
      const datos = (r.activosPorEstado || []).filter((x) => x.total > 0);
      const total = datos.reduce((s, x) => s + x.total, 0);
      if (!total) {
        caja.innerHTML = ui.vacio({
          titulo: 'Sin activos registrados',
          texto: 'Cuando se registre el primer activo aparece aquí su reparto entre activo, mantenimiento y baja.',
        });
        return;
      }
      // Orden fijo: el color sigue al estado.
      const conocidos = ESTADOS.map(([e]) => e);
      const filas = [
        ...ESTADOS.filter(([e]) => datos.some((x) => x.estado === e)).map(([e, tono]) => ({ estado: e, tono, total: datos.find((x) => x.estado === e).total })),
        ...datos.filter((x) => !conocidos.includes(x.estado)).map((x) => ({ estado: x.estado, tono: 'neutro', total: x.total })),
      ].map((f) => ({ ...f, nombre: (ui.ESTADO_ACTIVO[f.estado] || [f.estado])[0], pct: Math.round((f.total / total) * 100) }));
      const descripcion = filas.map((f) => `${f.nombre}: ${f.total}`).join(', ');

      caja.innerHTML = ui.html`
        <div class="dona">
          <svg class="anillo anillo--dona" viewBox="0 0 120 120" role="img" aria-label="${descripcion}">
            <circle class="anillo-fondo" cx="60" cy="60" r="${RADIO}"></circle>
            ${tramos(filas.map((f) => ({ fraccion: f.total / total, tono: f.tono })))}
            <text class="anillo-valor" x="60" y="62" text-anchor="middle">${ui.fmt.numero(total)}</text>
            <text class="anillo-texto" x="60" y="76" text-anchor="middle">${total === 1 ? 'activo' : 'activos'}</text>
          </svg>
          <ul class="dona-leyenda">
            ${filas.map((f) => ui.html`
              <li><i class="fondo-${f.tono}"></i><span>${f.nombre}</span><strong class="num">${ui.fmt.numero(f.total)}</strong><span class="dona-pct num">${f.pct}%</span></li>`)}
          </ul>
        </div>`;
    } catch (e) {
      caja.innerHTML = '<div class="hoja-cuerpo"></div>';
      ui.error(e, { en: caja.firstElementChild });
    }
  }

  // Vencidos y por vencer
  function plazo(p) {
    const d = ui.fmt.diasHasta(p.fechaDevolucionPactada);
    if (p.vencido || (d != null && d < 0)) {
      const n = Math.max(0, -(d ?? 0));
      return { nivel: 'vencido', tono: 'peligro', texto: n === 0 ? 'Venció hoy' : `Vencido hace ${plural(n, 'día', 'días')}` };
    }
    if (d == null) return { nivel: 'normal', tono: 'neutro', texto: 'Sin fecha pactada' };
    if (d === 0) return { nivel: 'pronto', tono: 'aviso', texto: 'Vence hoy' };
    if (d === 1) return { nivel: 'pronto', tono: 'aviso', texto: 'Vence mañana' };
    return { nivel: d <= 3 ? 'pronto' : 'normal', tono: d <= 3 ? 'aviso' : 'info', texto: `Vence en ${plural(d, 'día', 'días')}` };
  }

  async function pintarPlazos() {
    const caja = $('plazos');
    const nota = $('plazos-nota');
    caja.innerHTML = ui.html`<div class="hoja-cuerpo">${ui.cargandoBloque(4)}</div>`;
    try {
      const vigentes = await api.prestamos({ estado: 'VIGENTE' });
      vigentes.sort((a, b) => fecha(a.fechaDevolucionPactada) - fecha(b.fechaDevolucionPactada));
      const filas = vigentes.slice(0, MAX_FILAS).map((p) => ({ p, ...plazo(p) }));
      const vencidos = vigentes.filter((p) => plazo(p).nivel === 'vencido').length;

      nota.innerHTML = vigentes.length
        ? ui.html`${vencidos ? ui.html`<strong class="nota-peligro">${plural(vencidos, 'vencido', 'vencidos')}</strong> de ` : ''}${plural(vigentes.length, 'vigente', 'vigentes')}`
        : '';

      if (!vigentes.length) {
        caja.innerHTML = ui.vacio({
          titulo: 'Ningún préstamo vigente',
          texto: 'Cada préstamo nuevo aparece aquí con su fecha pactada de devolución.',
          accion: sesion.esAdmin() ? ui.html`<a class="boton boton--sm" href="prestamos.html?nuevo=">Registrar un préstamo</a>` : '',
        });
        return;
      }

      caja.innerHTML = ui.html`
        <ul class="renglones">
          ${filas.map(({ p, tono, texto }) => ui.html`
            <li class="renglon">
              <div class="renglon-cuerpo">
                ${ui.enlaceActivo(p.placaInventario)}
                <span class="renglon-texto" title="${p.activo?.descripcion || ''}">${p.activo ? p.activo.descripcion : 'Sin datos del activo'}</span>
                <span class="renglon-sub">Custodio <span class="mono">${p.numDocumento}</span></span>
              </div>
              <div class="renglon-lado">
                <span class="estado estado--${tono}">${texto}</span>
                <time class="renglon-sub mono" datetime="${p.fechaDevolucionPactada}" title="${ui.fmt.fechaHora(p.fechaDevolucionPactada)}">${ui.fmt.registro(p.fechaDevolucionPactada)}</time>
              </div>
            </li>`)}
        </ul>
        ${vigentes.length > MAX_FILAS
          ? ui.html`<p class="tarjeta-pie">Los ${MAX_FILAS} con fecha pactada más próxima, de ${vigentes.length} vigentes.</p>`
          : ''}`;
    } catch (e) {
      nota.innerHTML = '';
      caja.innerHTML = '<div class="hoja-cuerpo"></div>';
      ui.error(e, { en: caja.firstElementChild });
    }
  }

  // Últimos movimientos del kardex
  const AUTOMATICOS = new Set(['ALTA', 'BAJA', 'PRESTAMO', 'DEVOLUCION']);

  async function pintarKardex() {
    const caja = $('kardex');
    caja.innerHTML = ui.html`<div class="hoja-cuerpo">${ui.cargandoBloque(4)}</div>`;
    try {
      const movs = await api.movimientos();
      movs.sort((a, b) => (fecha(b.fechaMovimiento) - fecha(a.fechaMovimiento)) || (Number(b.idMovimiento) - Number(a.idMovimiento)));
      if (!movs.length) {
        caja.innerHTML = ui.vacio({
          titulo: 'El kardex está vacío',
          texto: 'El primer renglón llega solo cuando se registra un activo: inventario anota su alta al recibir el evento.',
        });
        return;
      }
      caja.innerHTML = ui.html`
        <ul class="renglones">
          ${movs.slice(0, MAX_FILAS).map((m) => ui.html`
            <li class="renglon">
              <div class="renglon-cuerpo">
                ${ui.enlaceActivo(m.placaInventario)}
                <span class="renglon-texto">${ui.TIPO_MOVIMIENTO[m.tipoMovimiento] || m.tipoMovimiento}${m.activo ? ui.html`<span class="renglon-tenue"> · ${m.activo.descripcion}</span>` : ''}</span>
                <span class="renglon-sub">${AUTOMATICOS.has(m.tipoMovimiento) ? 'Por evento Kafka' : 'Manual'} · Documento <span class="mono">${m.numDocumento}</span></span>
              </div>
              <div class="renglon-lado">
                <time class="renglon-sub" datetime="${m.fechaMovimiento}" title="${ui.fmt.fechaHora(m.fechaMovimiento)}">${ui.fmt.relativa(m.fechaMovimiento)}</time>
              </div>
            </li>`)}
        </ul>`;
    } catch (e) {
      caja.innerHTML = '<div class="hoja-cuerpo"></div>';
      ui.error(e, { en: caja.firstElementChild });
    }
  }

  // Notificaciones recientes
  async function pintarAvisos() {
    const caja = $('avisos');
    const nota = $('avisos-nota');
    caja.innerHTML = ui.html`<div class="hoja-cuerpo">${ui.cargandoBloque(4)}</div>`;
    try {
      const pagina = await api.notificaciones({ size: MAX_FILAS });
      const items = pagina.items || [];
      nota.textContent = pagina.totalElements ? `${ui.fmt.numero(pagina.noLeidas)} sin leer de ${ui.fmt.numero(pagina.totalElements)}` : '';
      if (!items.length) {
        caja.innerHTML = ui.vacio({
          titulo: 'Bandeja vacía',
          texto: `Aquí llegan los avisos de préstamo registrado, devuelto y vencido dirigidos a ${usuario.email || 'tu correo'}.`,
        });
        return;
      }
      caja.innerHTML = ui.html`
        <ul class="renglones">
          ${items.map((n) => ui.html`
            <li class="renglon renglon--enlace${n.leida ? '' : ' renglon--nueva'}">
              <a class="renglon-cuerpo" href="notificaciones.html">
                <span class="renglon-texto">${n.titulo}</span>
                <span class="renglon-sub">${n.leida ? '' : 'Sin leer · '}<time datetime="${n.creadaEn}" title="${ui.fmt.fechaHora(n.creadaEn)}">${ui.fmt.relativa(n.creadaEn)}</time></span>
              </a>
            </li>`)}
        </ul>`;
    } catch (e) {
      nota.textContent = '';
      caja.innerHTML = '<div class="hoja-cuerpo"></div>';
      ui.error(e, { en: caja.firstElementChild });
    }
  }

  const resumen = api.resumen();
  pintarCifras(resumen);
  pintarEstados(resumen);
  pintarPlazos();
  pintarKardex();
  pintarAvisos();
})();
