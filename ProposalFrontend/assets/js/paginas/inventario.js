// Inventario: kardex de movimientos y asignaciones.
(function () {
  'use strict';
  const { ui, api, sesion } = ETER;
  if (!ui.montar({ pagina: 'inventario' })) return;

  const html = ui.html;
  const $ = (id) => document.getElementById(id);

  // Tipos de movimiento
  /** Tipos que inventario registra solo, con su tópico. */
  const AUTOMATICOS = {
    ALTA: 'eter.activos.eventos.v1',
    BAJA: 'eter.activos.eventos.v1',
    PRESTAMO: 'eter.prestamos.eventos.v1',
    DEVOLUCION: 'eter.prestamos.eventos.v1',
  };
  const EVENTO_ORIGEN = {
    ALTA: 'ACTIVO_CREADO', BAJA: 'ACTIVO_DADO_DE_BAJA', PRESTAMO: 'PRESTAMO_CREADO', DEVOLUCION: 'PRESTAMO_DEVUELTO',
  };
  const SENTIDO = {
    ALTA: 'entrada', DEVOLUCION: 'entrada',
    PRESTAMO: 'salida', BAJA: 'salida',
    TRASLADO: 'interno', MANTENIMIENTO: 'interno', ASIGNACION: 'interno',
  };
  const SIGNO = { entrada: '+', salida: '−', interno: '·' };
  const NOMBRE_SENTIDO = { entrada: 'entrada', salida: 'salida', interno: 'movimiento interno' };
  const ORDEN_TIPOS = ['ALTA', 'DEVOLUCION', 'PRESTAMO', 'BAJA', 'TRASLADO', 'MANTENIMIENTO', 'ASIGNACION'];
  const MANUALES = ORDEN_TIPOS.filter((t) => !AUTOMATICOS[t]);
  const nombreTipo = (t) => ui.TIPO_MOVIMIENTO[t] || t;

  const RX_PLACA = /^[A-Za-z0-9]+(-[A-Za-z0-9]+)*$/;
  const RX_DOC = /^[A-Za-z0-9]{5,20}$/;
  const MSJ = {
    placa: 'solo admite letras, números y guiones (ej. AF-2026-0001)',
    doc: 'debe tener entre 5 y 20 letras o números, sin espacios ni símbolos',
    obligatorio: 'es obligatorio',
    tipo: 'elige un tipo de movimiento',
  };

  const sinTildes = (s) => String(s ?? '').normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase();
  const tiempo = (iso) => { const t = new Date(iso).getTime(); return Number.isNaN(t) ? 0 : t; };
  const porFechaDesc = (campo, id) => (a, b) =>
    tiempo(b[campo]) - tiempo(a[campo]) ||
    (id ? (Number(b[id]) - Number(a[id])) || String(b[id]).localeCompare(String(a[id])) : 0);
  const miDocumento = () => {
    const local = String(sesion.usuario()?.email || '').split('@')[0];
    return RX_DOC.test(local) ? local : '';
  };

  /** Clave de idempotencia nueva (UUID v4). */
  function nuevaClave() {
    try { if (window.crypto?.randomUUID) return crypto.randomUUID(); } catch { /* contexto no seguro */ }
    let b;
    try { b = crypto.getRandomValues(new Uint8Array(16)); } catch { b = Uint8Array.from({ length: 16 }, () => Math.floor(Math.random() * 256)); }
    b[6] = (b[6] & 0x0f) | 0x40; b[8] = (b[8] & 0x3f) | 0x80;
    const h = [...b].map((x) => x.toString(16).padStart(2, '0')).join('');
    return `${h.slice(0, 8)}-${h.slice(8, 12)}-${h.slice(12, 16)}-${h.slice(16, 20)}-${h.slice(20)}`;
  }

  function cambiarUrl(fn) {
    try {
      const u = new URL(location.href);
      fn(u.searchParams);
      history.replaceState(null, '', u);
    } catch { /* file:// no lo permite */ }
  }

  /** Valida como el servicio; lanza BAD_USER_INPUT. */
  function validar(reglas) {
    const errores = reglas.filter((r) => !r.ok).map(({ campo, mensaje }) => ({ campo, mensaje }));
    if (errores.length) throw new ETER.ApiError('BAD_USER_INPUT', 'Revisa los campos marcados.', { errores });
  }

  function errorEnFormulario(e, form, hueco) {
    if (e?.code === 'UNAUTHENTICATED') { ui.error(e); return; }
    if (e?.code === 'BAD_USER_INPUT') ui.marcarErrores(form, e.errores);
    if (e?.code === 'NOT_FOUND') ui.marcarErrores(form, [{ campo: 'placaInventario', mensaje: 'no existe en la universidad' }]);
    hueco.innerHTML = ui.alerta(e);
    (form.querySelector('[aria-invalid="true"]') || hueco).scrollIntoView({ block: 'nearest' });
    form.querySelector('[aria-invalid="true"]')?.focus();
  }

  // Íconos del HTML
  document.querySelectorAll('i[data-icono]').forEach((i) => { i.outerHTML = String(ui.icono(i.dataset.icono)); });

  // Permisos
  const btnMovimiento = $('btn-movimiento');
  const btnAsignacion = $('btn-asignacion');
  ui.soloAdmin(btnMovimiento, btnAsignacion);
  $('solo-lectura').hidden = sesion.esAdmin();

  // Pestañas (?tab=)
  const TABS = ['kardex', 'asignaciones'];
  const botonTab = (id) => $(`tab-${id}`);

  function mostrarTab(id, { foco = false } = {}) {
    if (!TABS.includes(id)) id = 'kardex';
    TABS.forEach((t) => {
      const activa = t === id;
      botonTab(t).setAttribute('aria-selected', String(activa));
      botonTab(t).tabIndex = activa ? 0 : -1;
      $(`panel-${t}`).hidden = !activa;
    });
    if (foco) botonTab(id).focus();
    cambiarUrl((p) => { if (id === 'kardex') p.delete('tab'); else p.set('tab', id); });
    ui.marcarNav(id === 'kardex' ? 'inventario' : 'asignaciones');
  }

  $('pestanas').addEventListener('click', (ev) => {
    const b = ev.target.closest('[role="tab"]');
    if (b) mostrarTab(b.id.replace('tab-', ''));
  });
  $('pestanas').addEventListener('keydown', (ev) => {
    const actual = TABS.findIndex((t) => botonTab(t).getAttribute('aria-selected') === 'true');
    const destino = { ArrowRight: actual + 1, ArrowLeft: actual - 1, Home: 0, End: TABS.length - 1 }[ev.key];
    if (destino === undefined) return;
    ev.preventDefault();
    mostrarTab(TABS[(destino + TABS.length) % TABS.length], { foco: true });
  });

  const contarTab = (id, n) => { document.querySelector(`[data-cuenta="${id}"]`).textContent = n == null ? '' : String(n); };

  // Kardex
  const kardex = { datos: [], cargado: false };
  const filtroK = { q: ui.param('placa') || '', tipo: '', doc: '' };

  const tablaK = $('tabla-kardex');
  const filasK = $('filas-kardex');
  const marcoK = $('marco-kardex');
  const avisoK = $('aviso-kardex');
  const pieK = $('pie-kardex');
  const conteosK = $('conteos-kardex');
  const periodoK = $('periodo-kardex');
  const buscarK = $('k-buscar');
  const tipoK = $('k-tipo');
  const docK = $('k-doc');
  const limpiarK = $('k-limpiar');

  tipoK.insertAdjacentHTML('beforeend', String(html`${ORDEN_TIPOS.map((t) => html`<option value="${t}">${nombreTipo(t)}</option>`)}`));
  buscarK.value = filtroK.q;

  function filtrarKardex({ conTipo = true } = {}) {
    const q = sinTildes(filtroK.q.trim());
    const doc = sinTildes(filtroK.doc.trim());
    return kardex.datos.filter((m) =>
      (!conTipo || !filtroK.tipo || m.tipoMovimiento === filtroK.tipo) &&
      (!doc || sinTildes(m.numDocumento).includes(doc)) &&
      (!q || sinTildes(m.placaInventario).includes(q) || sinTildes(m.activo?.descripcion).includes(q)));
  }

  const signo = (sentido) => html`<span class="signo signo--${sentido}" aria-hidden="true">${SIGNO[sentido]}</span>`;

  function rolDocumento(tipo) {
    if (tipo === 'PRESTAMO' || tipo === 'DEVOLUCION') return 'custodio';
    return 'responsable';
  }

  const filaMovimiento = (m) => {
    const sentido = SENTIDO[m.tipoMovimiento] || 'interno';
    const topico = AUTOMATICOS[m.tipoMovimiento];
    return html`
      <tr>
        <td class="folio-n"></td>
        <td class="min"><time class="mono" datetime="${m.fechaMovimiento}" title="${ui.fmt.fechaHora(m.fechaMovimiento)}">${ui.fmt.registro(m.fechaMovimiento)}</time></td>
        <td class="min">${ui.enlaceActivo(m.placaInventario)}</td>
        <td class="col-activo">${m.activo
          ? html`<span class="principal">${m.activo.descripcion}</span>`
          : html`<span class="tenue">Sin datos del activo</span>`}</td>
        <td class="min">
          <span class="mov" title="${nombreTipo(m.tipoMovimiento)}: ${NOMBRE_SENTIDO[sentido]}">${signo(sentido)}${nombreTipo(m.tipoMovimiento)}<span class="sr"> (${NOMBRE_SENTIDO[sentido]})</span></span>
        </td>
        <td class="min"><span class="mono">${m.numDocumento}</span><span class="sub">${rolDocumento(m.tipoMovimiento)}</span></td>
        <td class="min">${topico
          ? html`<span class="origen origen--kafka" title="Lo asentó inventario al consumir ${EVENTO_ORIGEN[m.tipoMovimiento]} de ${topico}">Automático (Kafka)</span><span class="sub mono">${topico.replace(/^eter\./, '')}</span>`
          : html`<span class="origen">Manual</span><span class="sub">registrado en ETER</span>`}</td>
      </tr>`;
  };

  function pintarResumen(base, visibles) {
    const cuenta = Object.fromEntries(ORDEN_TIPOS.map((t) => [t, 0]));
    base.forEach((m) => { if (m.tipoMovimiento in cuenta) cuenta[m.tipoMovimiento] += 1; });
    const entradas = cuenta.ALTA + cuenta.DEVOLUCION;
    const salidas = cuenta.PRESTAMO + cuenta.BAJA;

    if (visibles.length) {
      const fechas = visibles.map((m) => m.fechaMovimiento).sort((a, b) => tiempo(a) - tiempo(b));
      periodoK.innerHTML = html`<span class="mono">${ui.fmt.fecha(fechas[0])} → ${ui.fmt.fecha(fechas.at(-1))}</span> · ${visibles.length} ${visibles.length === 1 ? 'asiento visible' : 'asientos visibles'}`;
    } else {
      periodoK.textContent = 'Sin asientos en la vista';
    }

    conteosK.innerHTML = html`
      ${ORDEN_TIPOS.map((t) => html`
        <button type="button" class="conteo" data-tipo="${t}" aria-pressed="${String(filtroK.tipo === t)}"
                title="${filtroK.tipo === t ? 'Quitar el filtro' : `Ver solo ${nombreTipo(t).toLowerCase()}`}">
          <span class="conteo-tipo">${signo(SENTIDO[t])}<span class="conteo-nombre">${nombreTipo(t)}</span></span>
          <span class="conteo-n${cuenta[t] ? '' : ' conteo-n--cero'}">${cuenta[t]}</span>
          <span class="conteo-origen">${AUTOMATICOS[t] ? 'Kafka' : 'manual'}</span>
        </button>`)}
      <button type="button" class="conteo conteo--total" data-tipo="" aria-pressed="${String(!filtroK.tipo)}" title="Ver todos los tipos">
        <span class="conteo-tipo">Total</span>
        <span class="conteo-n">${base.length}</span>
        <span class="conteo-origen"><span>${entradas} entradas</span><span>${salidas} salidas</span></span>
      </button>`;
  }

  const paginasK = ui.paginador($('paginador-kardex'), { alCambiar: () => pintarKardex() });

  function pintarKardex() {
    if (!kardex.cargado) return;
    const base = filtrarKardex({ conTipo: false });
    const visibles = filtroK.tipo ? base.filter((m) => m.tipoMovimiento === filtroK.tipo) : base;
    const hayFiltro = !!(filtroK.q.trim() || filtroK.tipo || filtroK.doc.trim());
    limpiarK.hidden = !hayFiltro;
    pintarResumen(base, visibles);
    $('resumen-kardex').removeAttribute('aria-busy');
    const pagina = paginasK.cortar(visibles);

    if (!visibles.length) {
      marcoK.hidden = true;
      avisoK.innerHTML = kardex.datos.length
        ? ui.vacio({
          titulo: 'Ningún asiento coincide',
          texto: 'Ningún movimiento cumple la búsqueda, el tipo y el documento elegidos.',
          accion: html`<button type="button" class="boton boton--sm" data-limpiar>Limpiar filtros</button>`,
        })
        : ui.vacio({
          titulo: 'El kardex está en blanco',
          texto: 'Todavía no hay movimientos. Cuando se registre un activo o un préstamo, su asiento aparece aquí solo, unos segundos después.',
        });
      pieK.textContent = '';
      return;
    }

    avisoK.innerHTML = '';
    marcoK.hidden = false;
    // Folio cronológico: el más antiguo es el 01.
    tablaK.style.counterReset = `folio ${visibles.length + 1 - visibles.indexOf(pagina[0])}`;
    filasK.innerHTML = html`${pagina.map(filaMovimiento)}`;
    pieK.textContent = `${visibles.length} de ${kardex.datos.length} movimientos, del más reciente al más antiguo. El folio numera los asientos de esta vista en orden cronológico.`;
  }

  async function cargarKardex() {
    avisoK.innerHTML = '';
    marcoK.hidden = false;
    filasK.innerHTML = ui.cargandoFilas(7, 6);
    try {
      const lista = await api.movimientos();
      kardex.datos = (lista || []).slice().sort(porFechaDesc('fechaMovimiento', 'idMovimiento'));
      kardex.cargado = true;
      contarTab('kardex', kardex.datos.length);
      pintarKardex();
    } catch (e) {
      kardex.cargado = false;
      marcoK.hidden = true;
      filasK.innerHTML = '';
      conteosK.innerHTML = '';
      periodoK.textContent = 'Sin datos';
      pieK.textContent = '';
      ui.error(e, { en: avisoK });
    }
  }

  const repintarDemorado = ui.demorar(pintarKardex, 150);
  buscarK.addEventListener('input', () => {
    filtroK.q = buscarK.value;
    if (ui.param('placa') !== null) cambiarUrl((p) => p.delete('placa'));
    repintarDemorado();
  });
  docK.addEventListener('input', () => { filtroK.doc = docK.value; repintarDemorado(); });
  tipoK.addEventListener('change', () => { filtroK.tipo = tipoK.value; pintarKardex(); });
  $('filtros-kardex').addEventListener('submit', (ev) => ev.preventDefault());

  function limpiarFiltrosKardex() {
    filtroK.q = ''; filtroK.tipo = ''; filtroK.doc = '';
    buscarK.value = ''; tipoK.value = ''; docK.value = '';
    cambiarUrl((p) => p.delete('placa'));
    pintarKardex();
    buscarK.focus();
  }
  limpiarK.addEventListener('click', limpiarFiltrosKardex);
  avisoK.addEventListener('click', (ev) => { if (ev.target.closest('[data-limpiar]')) limpiarFiltrosKardex(); });
  $('k-recargar').addEventListener('click', cargarKardex);

  conteosK.addEventListener('click', (ev) => {
    const b = ev.target.closest('[data-tipo]');
    if (!b) return;
    const t = b.dataset.tipo;
    filtroK.tipo = t && filtroK.tipo === t ? '' : t;
    tipoK.value = filtroK.tipo;
    pintarKardex();
    conteosK.querySelector(`[data-tipo="${filtroK.tipo}"]`)?.focus();
  });

  // Asignaciones
  const asig = { datos: [], cargado: false, q: '' };
  const filasA = $('filas-asignaciones');
  const marcoA = $('marco-asignaciones');
  const avisoA = $('aviso-asignaciones');
  const pieA = $('pie-asignaciones');
  const buscarA = $('a-buscar');

  const filaAsignacion = (x) => html`
    <tr>
      <td class="min">${ui.enlaceActivo(x.placaInventario)}</td>
      <td class="col-activo">${x.activo
        ? html`<span class="principal">${x.activo.descripcion}</span><span class="sub">Sede <span class="mono">${x.activo.codSede}</span></span>`
        : html`<span class="tenue">Sin datos del activo</span>`}</td>
      <td class="min">${x.activo ? ui.estadoActivo(x.activo.estado) : '—'}</td>
      <td class="min"><span class="mono">${x.numDocumento}</span></td>
      <td class="min"><time class="mono" datetime="${x.fechaHoraActa}" title="${ui.fmt.fechaHora(x.fechaHoraActa)}">${ui.fmt.registro(x.fechaHoraActa)}</time></td>
    </tr>`;

  const paginasA = ui.paginador($('paginador-asignaciones'), { alCambiar: () => pintarAsignaciones() });

  function pintarAsignaciones() {
    if (!asig.cargado) return;
    const q = sinTildes(asig.q.trim());
    const visibles = asig.datos.filter((x) => !q ||
      sinTildes(x.placaInventario).includes(q) || sinTildes(x.numDocumento).includes(q) || sinTildes(x.activo?.descripcion).includes(q));
    const pagina = paginasA.cortar(visibles);

    if (!visibles.length) {
      marcoA.hidden = true;
      avisoA.innerHTML = asig.datos.length
        ? ui.vacio({ titulo: 'Ninguna acta coincide', texto: 'Prueba con otra placa, otra descripción o el documento completo del custodio.' })
        : ui.vacio({ titulo: 'Sin actas de asignación', texto: 'Ningún activo está asignado todavía. Usa «Nueva asignación» para dejar un activo a cargo de un custodio.' });
      pieA.textContent = '';
      return;
    }
    avisoA.innerHTML = '';
    marcoA.hidden = false;
    filasA.innerHTML = html`${pagina.map(filaAsignacion)}`;
    const custodios = new Set(visibles.map((x) => x.numDocumento)).size;
    pieA.textContent = `${visibles.length} de ${asig.datos.length} actas · ${custodios} ${custodios === 1 ? 'custodio' : 'custodios'}, de la más reciente a la más antigua.`;
  }

  async function cargarAsignaciones() {
    avisoA.innerHTML = '';
    marcoA.hidden = false;
    filasA.innerHTML = ui.cargandoFilas(5, 4);
    try {
      const lista = await api.asignaciones();
      asig.datos = (lista || []).slice().sort(porFechaDesc('fechaHoraActa'));
      asig.cargado = true;
      contarTab('asignaciones', asig.datos.length);
      pintarAsignaciones();
    } catch (e) {
      asig.cargado = false;
      marcoA.hidden = true;
      filasA.innerHTML = '';
      pieA.textContent = '';
      ui.error(e, { en: avisoA });
    }
  }

  const repintarAsigDemorado = ui.demorar(pintarAsignaciones, 150);
  buscarA.addEventListener('input', () => { asig.q = buscarA.value; repintarAsigDemorado(); });
  $('filtros-asignaciones').addEventListener('submit', (ev) => ev.preventDefault());
  $('a-recargar').addEventListener('click', cargarAsignaciones);

  // Placas conocidas
  let activos = null;
  async function cargarActivos() {
    if (activos) return activos;
    try {
      activos = await api.activos();
    } catch {
      return null; // el servicio valida igual
    }
    $('lista-placas').innerHTML = html`${activos.map((a) => html`<option value="${a.placaInventario}">${a.descripcion} · ${a.codSede}</option>`)}`;
    return activos;
  }

  function pista(input) {
    const salida = document.getElementById(input.getAttribute('aria-describedby'));
    const v = input.value.trim();
    salida.classList.remove('pista--ok', 'pista--mal');
    if (!v) { salida.textContent = salida.dataset.base; return; }
    if (!RX_PLACA.test(v)) { salida.textContent = 'Solo letras, números y guiones (ej. AF-2026-0001).'; salida.classList.add('pista--mal'); return; }
    if (!activos) { salida.textContent = salida.dataset.base; return; }
    const a = activos.find((x) => x.placaInventario === v);
    if (a) {
      const estado = (ui.ESTADO_ACTIVO[a.estado] || [a.estado])[0];
      salida.textContent = `${a.descripcion} · ${a.codSede} · ${estado}`;
      salida.classList.add('pista--ok');
    } else {
      salida.textContent = 'No hay un activo con esta placa en la universidad.';
      salida.classList.add('pista--mal');
    }
  }

  document.querySelectorAll('[data-cerrar]').forEach((b) => b.addEventListener('click', () => ui.cerrar(b.closest('dialog'))));

  // Diálogo: registrar movimiento
  const dlgMov = $('dlg-movimiento');
  const formMov = $('form-movimiento');
  const alertaMov = $('alerta-movimiento');
  const placaMov = $('m-placa');
  const tipoMov = $('m-tipo');
  const avisoKafka = $('m-aviso-kafka');

  tipoMov.innerHTML = html`
    <option value="">Elige el tipo…</option>
    <optgroup label="Se registran a mano">
      ${MANUALES.map((t) => html`<option value="${t}">${nombreTipo(t)}</option>`)}
    </optgroup>
    <optgroup label="Llegan solos por Kafka">
      ${ORDEN_TIPOS.filter((t) => AUTOMATICOS[t]).map((t) => html`<option value="${t}">${nombreTipo(t)}</option>`)}
    </optgroup>`;

  function avisoTipo() {
    const t = tipoMov.value;
    avisoKafka.hidden = !AUTOMATICOS[t];
    if (AUTOMATICOS[t]) {
      $('m-aviso-kafka-texto').textContent =
        `Inventario asienta ${nombreTipo(t).toLowerCase()} al consumir ${EVENTO_ORIGEN[t]} de ${AUTOMATICOS[t]}. ` +
        'Regístralo a mano solo si ese evento no llegó (por ejemplo, si quedó en el DLT); si no, el kardex queda con el asiento repetido.';
    }
  }
  tipoMov.addEventListener('change', avisoTipo);
  placaMov.addEventListener('input', () => pista(placaMov));

  function abrirMovimiento() {
    formMov.reset();
    ui.limpiarErrores(formMov);
    alertaMov.innerHTML = '';
    const placaVista = filtroK.q.trim();
    if (kardex.datos.some((m) => m.placaInventario === placaVista)) placaMov.value = placaVista;
    formMov.elements.fechaMovimiento.value = ui.fechaInput();
    formMov.elements.numDocumento.value = miDocumento();
    const clave = nuevaClave();
    formMov.elements.idempotencyKey.value = clave;
    $('m-clave').textContent = clave;
    avisoTipo();
    pista(placaMov);
    ui.abrir(dlgMov);
    cargarActivos().then(() => pista(placaMov));
  }
  btnMovimiento.addEventListener('click', abrirMovimiento);

  formMov.addEventListener('submit', async (ev) => {
    ev.preventDefault();
    const btn = $('m-enviar');
    ui.limpiarErrores(formMov);
    alertaMov.innerHTML = '';
    const d = ui.datosForm(formMov);
    const input = {
      placaInventario: d.placaInventario,
      tipoMovimiento: d.tipoMovimiento,
      fechaMovimiento: ui.aLocalDateTime(d.fechaMovimiento),
      numDocumento: d.numDocumento,
      idempotencyKey: d.idempotencyKey,
    };
    ui.ocupado(btn, true);
    try {
      validar([
        { campo: 'placaInventario', ok: RX_PLACA.test(input.placaInventario || ''), mensaje: input.placaInventario ? MSJ.placa : MSJ.obligatorio },
        { campo: 'tipoMovimiento', ok: !!SENTIDO[input.tipoMovimiento], mensaje: MSJ.tipo },
        { campo: 'fechaMovimiento', ok: !!input.fechaMovimiento, mensaje: MSJ.obligatorio },
        { campo: 'numDocumento', ok: RX_DOC.test(input.numDocumento || ''), mensaje: input.numDocumento ? MSJ.doc : MSJ.obligatorio },
      ]);
      const m = await api.registrarMovimiento(input);
      const yaEstaba = kardex.datos.some((x) => String(x.idMovimiento) === String(m.idMovimiento));
      ui.cerrar(dlgMov);
      ui.toast(yaEstaba
        ? `Ese movimiento ya estaba en el kardex (misma clave ${input.idempotencyKey.slice(0, 8)}…). No se duplicó.`
        : `${nombreTipo(m.tipoMovimiento)} de ${m.placaInventario} asentado en el kardex.`);
      mostrarTab('kardex');
      await cargarKardex();
    } catch (e) {
      errorEnFormulario(e, formMov, alertaMov);
    } finally {
      ui.ocupado(btn, false);
    }
  });

  // Diálogo: nueva asignación
  const dlgAsig = $('dlg-asignacion');
  const formAsig = $('form-asignacion');
  const alertaAsig = $('alerta-asignacion');
  const placaAsig = $('s-placa');
  let claveAsig = '';

  placaAsig.addEventListener('input', () => pista(placaAsig));

  function abrirAsignacion() {
    formAsig.reset();
    ui.limpiarErrores(formAsig);
    alertaAsig.innerHTML = '';
    formAsig.elements.fechaHoraActa.value = ui.fechaInput();
    $('s-asentar').checked = true;
    claveAsig = nuevaClave();
    pista(placaAsig);
    ui.abrir(dlgAsig);
    cargarActivos().then(() => pista(placaAsig));
  }
  btnAsignacion.addEventListener('click', abrirAsignacion);

  formAsig.addEventListener('submit', async (ev) => {
    ev.preventDefault();
    const btn = $('s-enviar');
    ui.limpiarErrores(formAsig);
    alertaAsig.innerHTML = '';
    const d = ui.datosForm(formAsig);
    const input = {
      placaInventario: d.placaInventario,
      numDocumento: d.numDocumento,
      fechaHoraActa: ui.aLocalDateTime(d.fechaHoraActa),
    };
    const asentar = $('s-asentar').checked;
    ui.ocupado(btn, true);
    try {
      validar([
        { campo: 'placaInventario', ok: RX_PLACA.test(input.placaInventario || ''), mensaje: input.placaInventario ? MSJ.placa : MSJ.obligatorio },
        { campo: 'numDocumento', ok: RX_DOC.test(input.numDocumento || ''), mensaje: input.numDocumento ? MSJ.doc : MSJ.obligatorio },
        { campo: 'fechaHoraActa', ok: !!input.fechaHoraActa, mensaje: MSJ.obligatorio },
      ]);
      const a = await api.crearAsignacion(input);
      let asentado = false;
      if (asentar) {
        try {
          await api.registrarMovimiento({
            placaInventario: a.placaInventario,
            tipoMovimiento: 'ASIGNACION',
            fechaMovimiento: input.fechaHoraActa,
            numDocumento: a.numDocumento,
            idempotencyKey: claveAsig,
          });
          asentado = true;
        } catch (e2) {
          const { titulo, detalle } = ui.mensajeError(e2);
          ui.toast(`El acta quedó registrada, pero el kardex no asentó la asignación. ${titulo}: ${detalle}`, { tipo: 'error', ms: 9000 });
        }
      }
      ui.cerrar(dlgAsig);
      ui.toast(`Acta registrada: ${a.placaInventario} queda a cargo del documento ${a.numDocumento}.${asentado ? ' Asignación asentada en el kardex.' : ''}`);
      mostrarTab('asignaciones');
      await Promise.all([cargarAsignaciones(), asentado ? cargarKardex() : null]);
    } catch (e) {
      errorEnFormulario(e, formAsig, alertaAsig);
    } finally {
      ui.ocupado(btn, false);
    }
  });

  // Arranque
  mostrarTab(ui.param('tab') === 'asignaciones' ? 'asignaciones' : 'kardex');
  cargarKardex();
  cargarAsignaciones();
})();
