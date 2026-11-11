/*
 * ETER · Registro de activos (activos.html)
 *
 * Carga api.activos() una sola vez y los préstamos vigentes en paralelo; filtra
 * en el cliente por estado, sede y texto. Parámetros de URL:
 *   ?estado=ACTIVO|MANTENIMIENTO|BAJA   ?sede=SED-01   ?q=texto   ?nuevo=1
 */
(function () {
  'use strict';
  const { ui, api, sesion } = ETER;
  if (!ui.montar({ pagina: 'activos' })) return;

  const $ = (s, raiz = document) => raiz.querySelector(s);
  document.querySelectorAll('[data-icono]').forEach((i) => { i.outerHTML = String(ui.icono(i.dataset.icono)); });

  const ESTADOS = ['ACTIVO', 'MANTENIMIENTO', 'BAJA'];
  const el = {
    filas: $('#filas'),
    registro: $('#registro'),
    error: $('#lista-error'),
    vacia: $('#lista-vacia'),
    aviso: $('#aviso-prestamos'),
    resumen: $('#registro-resumen'),
    total: $('#registro-total'),
    segmentos: $('#segmentos'),
    sede: $('#f-sede'),
    buscar: $('#f-buscar'),
    btnRegistrar: $('#btn-registrar'),
    dlg: $('#dlg-registrar'),
    form: $('#form-registrar'),
    alerta: $('#registrar-alerta'),
    enviar: $('#r-enviar'),
    sedes: $('#lista-sedes'),
    sugerencia: $('#r-placa-sugerencia'),
    valor: $('#r-valor'),
    valorVista: $('#r-valor-vista'),
  };
  const paginas = ui.paginador($('#paginador'), { alCambiar: () => aplicar() });

  const estadoUrl = (ui.param('estado') || '').toUpperCase();
  const filtro = {
    estado: ESTADOS.includes(estadoUrl) ? estadoUrl : '',
    sede: ui.param('sede') || '',
    texto: ui.param('q') || '',
  };
  const abrirAlCargar = ui.param('nuevo') === '1';
  el.buscar.value = filtro.texto;

  let activos = [];
  let vigentes = new Map(); // placa -> préstamo vigente
  let prestamosOk = true;
  let sugerida = null;

  ui.soloAdmin(el.btnRegistrar);

  // ── Utilidades ────────────────────────────────────────────────────────────
  const normalizar = (s) => String(s ?? '').normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase().trim();
  const plural = (n, uno, varios) => `${n} ${n === 1 ? uno : varios}`;

  function textoPlazo(p) {
    const d = ui.fmt.diasHasta(p.fechaDevolucionPactada);
    if (p.vencido) {
      if (d == null || d >= 0) return { vencido: true, texto: 'Vencido hoy' };
      return { vencido: true, texto: `Vencido hace ${plural(-d, 'día', 'días')}` };
    }
    if (d === 0) return { vencido: false, texto: 'Vence hoy' };
    if (d === 1) return { vencido: false, texto: 'Vence mañana' };
    return { vencido: false, texto: `Vence en ${plural(d, 'día', 'días')}` };
  }

  /** Siguiente placa libre de la serie más usada (AF-2026-0012 -> AF-2026-0013). */
  function siguientePlaca() {
    const series = new Map();
    for (const a of activos) {
      const m = /^(.*?)(\d+)$/.exec(a.placaInventario);
      if (!m) continue;
      const s = series.get(m[1]) || { n: 0, max: 0, ancho: 0 };
      s.n += 1;
      s.max = Math.max(s.max, Number(m[2]));
      s.ancho = Math.max(s.ancho, m[2].length);
      series.set(m[1], s);
    }
    let mejor = null;
    for (const [prefijo, s] of series) if (!mejor || s.n > mejor.s.n) mejor = { prefijo, s };
    if (!mejor) return null;
    const usadas = new Set(activos.map((a) => a.placaInventario));
    let n = mejor.s.max + 1;
    let placa = mejor.prefijo + String(n).padStart(mejor.s.ancho, '0');
    while (usadas.has(placa)) placa = mejor.prefijo + String(++n).padStart(mejor.s.ancho, '0');
    return placa.length <= 50 ? placa : null;
  }

  function sincronizarUrl() {
    try {
      const u = new URL(location.href);
      const poner = (k, v) => (v ? u.searchParams.set(k, v) : u.searchParams.delete(k));
      poner('estado', filtro.estado);
      poner('sede', filtro.sede);
      poner('q', filtro.texto.trim());
      u.searchParams.delete('nuevo');
      if (u.href !== location.href) history.replaceState(null, '', u);
    } catch { /* desde file:// algunos navegadores no lo permiten; no es grave */ }
  }

  // ── Pintado ───────────────────────────────────────────────────────────────
  function celdaPrestamo(p) {
    if (!prestamosOk) return ui.html`<span class="tenue" title="El servicio de préstamos no respondió">sin dato</span>`;
    if (!p) return ui.html`<span class="guion" aria-hidden="true">—</span><span class="sr">Sin préstamo vigente</span>`;
    const plazo = textoPlazo(p);
    return ui.html`<span class="mono" title="Documento del custodio">${p.numDocumento}</span><span class="sub${plazo.vencido ? ' plazo-vencido' : ''}">${plazo.texto}</span>`;
  }

  const fila = (a) => ui.html`
    <tr class="fila-enlace" data-placa="${a.placaInventario}" data-estado="${a.estado}">
      <td class="min">${ui.enlaceActivo(a.placaInventario)}</td>
      <td class="principal celda-descripcion">${a.descripcion}<span class="sub">UNSPSC <span class="mono">${a.codUnspsc}</span></span></td>
      <td class="min"><span class="mono">${a.codSede}</span></td>
      <td class="min">${ui.estadoActivo(a.estado)}</td>
      <td class="celda-prestamo">${celdaPrestamo(vigentes.get(a.placaInventario))}</td>
      <td class="der min celda-valor">${ui.fmt.moneda(a.valorContable)}</td>
    </tr>`;

  function mostrarVacio(contenido) {
    el.registro.hidden = true;
    el.vacia.hidden = false;
    el.vacia.innerHTML = contenido;
    ui.soloAdmin(el.vacia.querySelector('[data-accion="registrar"]'));
  }

  function pintarSedes() {
    const cuenta = new Map();
    activos.forEach((a) => cuenta.set(a.codSede, (cuenta.get(a.codSede) || 0) + 1));
    if (filtro.sede && !cuenta.has(filtro.sede)) cuenta.set(filtro.sede, 0);
    const sedes = [...cuenta.keys()].sort((x, y) => x.localeCompare(y, 'es', { numeric: true }));
    el.sede.innerHTML = ui.html`<option value="">Todas las sedes</option>${sedes.map((s) =>
      ui.html`<option value="${s}">${s} (${cuenta.get(s)})</option>`)}`;
    el.sede.value = filtro.sede;
    el.sedes.innerHTML = ui.html`${sedes.filter((s) => cuenta.get(s)).map((s) =>
      ui.html`<option value="${s}">${plural(cuenta.get(s), 'activo', 'activos')}</option>`)}`;
  }

  function aplicar() {
    const q = normalizar(filtro.texto);
    const base = activos.filter((a) =>
      (!filtro.sede || a.codSede === filtro.sede) &&
      (!q || [a.placaInventario, a.descripcion, a.codUnspsc].some((c) => normalizar(c).includes(q))));

    const cuentas = { '': base.length, ACTIVO: 0, MANTENIMIENTO: 0, BAJA: 0 };
    base.forEach((a) => { if (a.estado in cuentas) cuentas[a.estado] += 1; });
    el.segmentos.querySelectorAll('[data-cuenta]').forEach((c) => { c.textContent = cuentas[c.dataset.cuenta] ?? 0; });
    el.segmentos.querySelectorAll('button').forEach((b) => b.setAttribute('aria-pressed', String(b.dataset.estado === filtro.estado)));

    const lista = base.filter((a) => !filtro.estado || a.estado === filtro.estado);
    sincronizarUrl();

    if (!activos.length) {
      mostrarVacio(ui.vacio({
        titulo: 'Todavía no hay activos registrados',
        texto: 'Registra el primero con su placa de inventario, el código UNSPSC y la sede donde queda. Inventario abre su kardex con el alta.',
        accion: ui.html`<button type="button" class="boton boton--primario" data-accion="registrar">${ui.icono('mas')}<span>Registrar activo</span></button>`,
      }));
      return;
    }
    if (!lista.length) {
      const condiciones = [
        filtro.estado && `estado ${ui.ESTADO_ACTIVO[filtro.estado][0].toLowerCase()}`,
        filtro.sede && `sede ${filtro.sede}`,
        filtro.texto.trim() && `búsqueda «${filtro.texto.trim()}»`,
      ].filter(Boolean);
      const cuales = condiciones.length > 1
        ? `${condiciones.slice(0, -1).join(', ')} y ${condiciones.at(-1)}`
        : condiciones[0];
      mostrarVacio(ui.vacio({
        titulo: 'Ningún activo coincide',
        texto: `No hay activos con ${cuales}. Revisa la placa o el código UNSPSC, o quita los filtros.`,
        accion: ui.html`<button type="button" class="boton" data-accion="quitar-filtros">Quitar filtros</button>`,
      }));
      return;
    }

    el.vacia.hidden = true;
    el.registro.hidden = false;
    el.filas.innerHTML = ui.html`${paginas.cortar(lista).map(fila)}`;

    const total = lista.reduce((s, a) => s + (Number(a.valorContable) || 0), 0);
    const prestados = lista.filter((a) => vigentes.has(a.placaInventario));
    const vencidos = prestados.filter((a) => vigentes.get(a.placaInventario).vencido).length;
    const filtrado = lista.length !== activos.length;
    el.resumen.innerHTML = ui.html`<strong>${lista.length}</strong> ${filtrado ? `de ${activos.length} ` : ''}${lista.length === 1 && !filtrado ? 'activo' : 'activos'}${
      prestamosOk ? ui.html` · <strong>${prestados.length}</strong> en préstamo${vencidos ? ui.html`, <strong class="resumen-vencidos">${plural(vencidos, 'vencido', 'vencidos')}</strong>` : ''}` : ''}`;
    el.total.textContent = ui.fmt.moneda(total);
  }

  // ── Carga ─────────────────────────────────────────────────────────────────
  async function cargar() {
    el.error.hidden = true;
    el.vacia.hidden = true;
    el.registro.hidden = false;
    el.filas.innerHTML = ui.cargandoFilas(6, 8);
    el.resumen.textContent = 'Cargando registro…';
    el.total.textContent = '—';

    const [ra, rp] = await Promise.allSettled([api.activos(), api.prestamos({ estado: 'VIGENTE' })]);
    if (ra.status === 'rejected') {
      el.registro.hidden = true;
      el.error.hidden = false;
      ui.error(ra.reason, { en: el.error });
      el.error.insertAdjacentHTML('beforeend', String(ui.html`
        <p class="reintentar"><button type="button" class="boton boton--sm" data-accion="reintentar">Reintentar</button></p>`));
      return;
    }

    activos = (ra.value || []).slice().sort((a, b) => a.placaInventario.localeCompare(b.placaInventario, 'es', { numeric: true }));
    if (rp.status === 'fulfilled') {
      prestamosOk = true;
      vigentes = new Map((rp.value || []).filter((p) => p.estado === 'VIGENTE').map((p) => [p.placaInventario, p]));
      el.aviso.hidden = true;
      el.aviso.innerHTML = '';
    } else {
      prestamosOk = false;
      vigentes = new Map();
      const { detalle } = ui.mensajeError(rp.reason);
      el.aviso.hidden = false;
      el.aviso.innerHTML = ui.html`
        <div class="alerta alerta--aviso" role="status">
          <strong>No se pudo consultar préstamos</strong>
          <span>${detalle}</span>
          <span>La columna Préstamo queda sin dato; el resto del registro está completo.</span>
        </div>`;
    }
    pintarSedes();
    sugerida = siguientePlaca();
    aplicar();
  }

  // ── Diálogo de registro ───────────────────────────────────────────────────
  function pintarSugerencia() {
    el.sugerencia.innerHTML = sugerida
      ? ui.html` Siguiente libre: <button type="button" class="boton-texto" data-accion="usar-placa" aria-label="Usar la placa ${sugerida}">${sugerida}</button>`
      : '';
    el.form.elements.placaInventario.placeholder = sugerida || 'AF-2026-0001';
  }

  function vistaValor() {
    const v = el.valor.value;
    const n = Number(v);
    el.valorVista.textContent = v !== '' && Number.isFinite(n) && n >= 0 ? `Queda en ${ui.fmt.moneda(n)}.` : '';
  }

  function abrirRegistro() {
    if (!sesion.esAdmin()) { ui.toast(ui.SIN_PERMISO, { tipo: 'aviso' }); return; }
    el.form.reset();
    ui.limpiarErrores(el.form);
    el.alerta.hidden = true;
    el.alerta.innerHTML = '';
    if (filtro.sede) el.form.elements.codSede.value = filtro.sede;
    pintarSugerencia();
    vistaValor();
    ui.ocupado(el.enviar, false);
    ui.abrir(el.dlg);
  }

  function falloRegistro(e) {
    if (e?.code === 'UNAUTHENTICATED') { ui.cerrar(el.dlg); ui.error(e); return; }
    if (e?.code === 'BAD_USER_INPUT') ui.marcarErrores(el.form, e.errores);
    if (e?.code === 'CONFLICT') {
      ui.marcarErrores(el.form, [{ campo: 'placaInventario', mensaje: 'Ya hay un activo con esta placa. Usa otra.' }]);
    }
    el.alerta.innerHTML = ui.alerta(e);
    el.alerta.hidden = false;
    const invalido = el.form.querySelector('[aria-invalid="true"]');
    if (invalido) invalido.focus();
    else el.alerta.scrollIntoView({ block: 'nearest' });
  }

  el.form.addEventListener('submit', async (ev) => {
    ev.preventDefault();
    const d = ui.datosForm(el.form);
    const input = {
      placaInventario: d.placaInventario,
      descripcion: d.descripcion,
      codUnspsc: d.codUnspsc,
      codSede: d.codSede,
      valorContable: Number(d.valorContable),
      estado: d.estado,
    };
    ui.limpiarErrores(el.form);
    el.alerta.hidden = true;
    ui.ocupado(el.enviar, true);
    try {
      const creado = await api.crearActivo(input);
      // El detalle muestra el aviso de Kafka y espera el ALTA en el kardex.
      location.href = `${ui.urlActivo(creado.placaInventario)}&creado=1`;
    } catch (e) {
      ui.ocupado(el.enviar, false);
      falloRegistro(e);
    }
  });
  el.valor.addEventListener('input', vistaValor);

  // ── Eventos ───────────────────────────────────────────────────────────────
  document.addEventListener('click', (ev) => {
    const cerrar = ev.target.closest('[data-cerrar]');
    if (cerrar) { ui.cerrar(cerrar.closest('dialog')); return; }
    const b = ev.target.closest('[data-accion]');
    if (!b) return;
    switch (b.dataset.accion) {
      case 'registrar': abrirRegistro(); break;
      case 'reintentar': cargar(); break;
      case 'usar-placa':
        el.form.elements.placaInventario.value = sugerida;
        el.form.elements.codUnspsc.focus();
        break;
      case 'quitar-filtros':
        filtro.estado = '';
        filtro.sede = '';
        filtro.texto = '';
        el.sede.value = '';
        el.buscar.value = '';
        aplicar();
        el.buscar.focus();
        break;
      default: break;
    }
  });

  el.segmentos.addEventListener('click', (ev) => {
    const b = ev.target.closest('button[data-estado]');
    if (!b) return;
    filtro.estado = b.dataset.estado;
    aplicar();
  });
  el.sede.addEventListener('change', () => { filtro.sede = el.sede.value; aplicar(); });
  el.buscar.addEventListener('input', ui.demorar(() => { filtro.texto = el.buscar.value; aplicar(); }, 200));

  // Filas clicables (el enlace de la placa sigue siendo el camino con teclado).
  el.filas.addEventListener('click', (ev) => {
    if (ev.target.closest('a, button')) return;
    if (String(window.getSelection?.() || '').trim()) return; // está seleccionando texto
    const tr = ev.target.closest('tr[data-placa]');
    if (!tr) return;
    const url = ui.urlActivo(tr.dataset.placa);
    if (ev.ctrlKey || ev.metaKey) window.open(url, '_blank');
    else location.href = url;
  });

  cargar().finally(() => { if (abrirAlCargar) abrirRegistro(); });
})();
