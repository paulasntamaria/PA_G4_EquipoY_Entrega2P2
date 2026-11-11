// Detalle de un activo (activo.html?placa=...).
(function () {
  'use strict';
  const { ui, api, sesion } = ETER;
  if (!ui.montar({ pagina: 'activos' })) return;

  const $ = (s) => document.querySelector(s);
  const placa = (ui.param('placa') || '').trim();
  const recienCreado = ui.param('creado') === '1';
  const MAX_KARDEX = 15;
  const ESPERA_MS = 3000;
  const INTENTOS = 3;
  const FRASE_MOV = { ALTA: 'el alta', BAJA: 'la baja', DEVOLUCION: 'la devolución' };

  const el = {
    cabecera: $('#cabecera'),
    detalle: $('#detalle'),
    dlgEditar: $('#dlg-editar'),
    formEditar: $('#form-editar'),
    alertaEditar: $('#editar-alerta'),
    enviarEditar: $('#e-enviar'),
    valorEditar: $('#e-valor'),
    vistaEditar: $('#e-valor-vista'),
    dlgDevolucion: $('#dlg-devolucion'),
    formDevolucion: $('#form-devolucion'),
    alertaDevolucion: $('#devolucion-alerta'),
    enviarDevolucion: $('#d-enviar'),
    resumenDevolucion: $('#devolucion-resumen'),
    dlgAsignar: $('#dlg-asignar'),
    formAsignar: $('#form-asignar'),
    alertaAsignar: $('#asignar-alerta'),
    enviarAsignar: $('#a-enviar'),
  };

  let activo = null;
  let esperando = null;
  let temporizador = null;
  let sedesPedidas = false;

  // Utilidades
  const plural = (n, uno, varios) => `${n} ${n === 1 ? uno : varios}`;
  const enc = encodeURIComponent;
  const desc = (campo) => (a, b) => String(b[campo] || '').localeCompare(String(a[campo] || ''));
  const contar = (a, tipo) => (a?.movimientos || []).filter((m) => m.tipoMovimiento === tipo).length;
  const inicioDia = (d) => { const x = new Date(d); x.setHours(0, 0, 0, 0); return x; };
  const ordenMov = (a, b) =>
    String(b.fechaMovimiento).localeCompare(String(a.fechaMovimiento)) ||
    String(b.idMovimiento).localeCompare(String(a.idMovimiento), 'es', { numeric: true });

  function plazo(p) {
    const d = ui.fmt.diasHasta(p.fechaDevolucionPactada);
    if (p.vencido) {
      if (d == null || d >= 0) return { vencido: true, etiqueta: 'Atraso', texto: 'Venció hoy' };
      return { vencido: true, etiqueta: 'Atraso', texto: `${plural(-d, 'día', 'días')} de atraso` };
    }
    if (d === 0) return { vencido: false, etiqueta: 'Plazo', texto: 'Vence hoy' };
    if (d === 1) return { vencido: false, etiqueta: 'Plazo', texto: 'Vence mañana' };
    return { vencido: false, etiqueta: 'Plazo', texto: `Faltan ${plural(d, 'día', 'días')}` };
  }

  function puntualidad(p) {
    const real = new Date(p.fechaDevolucionReal);
    const pactada = new Date(p.fechaDevolucionPactada);
    if (!(real > pactada)) return 'A tiempo';
    const dias = Math.round((inicioDia(real) - inicioDia(pactada)) / 86400000);
    return dias >= 1 ? `${plural(dias, 'día', 'días')} tarde` : 'Tarde, el mismo día';
  }

  /** Fecha y hora en dos renglones. */
  function dosRenglones(iso) {
    const [fecha, hora] = ui.fmt.registro(iso).split(' ');
    return ui.html`<span>${fecha}</span>${hora ? ui.html`<span class="hora">${hora}</span>` : ''}`;
  }

  const folio = (ultimo) => ui.html`
    <p class="folio"><a href="index.html">Patrimonio</a> / <a href="activos.html">Activos</a>${ultimo ? ui.html` / <span>${ultimo}</span>` : ''}</p>`;

  function quitarParametro(nombre) {
    try {
      const u = new URL(location.href);
      u.searchParams.delete(nombre);
      history.replaceState(null, '', u);
    } catch { /* file:// no lo permite */ }
  }

  // Estados de la página
  function pintarCargando() {
    el.cabecera.innerHTML = ui.html`
      <div class="cabecera-cuerpo">
        ${folio(placa)}
        <h1 class="titulo"><span class="sr">Cargando el activo ${placa}</span><span class="esqueleto esqueleto-titulo" aria-hidden="true"></span></h1>
      </div>`;
    el.detalle.className = 'columnas detalle';
    el.detalle.innerHTML = ui.html`
      <div class="pila detalle-principal"><div class="hoja hoja-cuerpo">${ui.cargandoBloque(5)}</div><div class="hoja hoja-cuerpo">${ui.cargandoBloque(3)}</div></div>
      <div class="pila detalle-lateral"><div class="hoja hoja-cuerpo">${ui.cargandoBloque(6)}</div></div>`;
  }

  function pintarInexistente() {
    document.title = 'Activo no encontrado · ETER Patrimonio';
    el.cabecera.innerHTML = ui.html`
      <div class="cabecera-cuerpo">${folio(placa)}<h1 class="titulo">${placa ? 'Activo no encontrado' : 'Falta la placa'}</h1></div>`;
    el.detalle.className = 'detalle-vacio';
    el.detalle.innerHTML = ui.vacio({
      titulo: placa ? `No existe el activo ${placa}` : 'No existe el activo: la dirección no trae placa',
      texto: placa
        ? 'La placa no aparece en el registro de esta universidad. Revisa que esté bien escrita o búscala en el registro de activos.'
        : 'Abre el detalle desde el registro de activos, o agrega ?placa=AF-2026-0001 a la dirección.',
      accion: ui.html`<a class="boton" href="activos.html">${ui.icono('volver')}<span>Ir al registro de activos</span></a>`,
    });
  }

  function pintarError(e) {
    el.cabecera.innerHTML = ui.html`
      <div class="cabecera-cuerpo">${folio(placa)}<h1 class="titulo">${placa}</h1></div>`;
    el.detalle.className = 'detalle-vacio';
    el.detalle.innerHTML = '<div id="detalle-error"></div>';
    const caja = $('#detalle-error');
    ui.error(e, { en: caja });
    caja.insertAdjacentHTML('beforeend', String(ui.html`
      <p class="reintentar"><button type="button" class="boton boton--sm" data-accion="reintentar">Reintentar</button></p>`));
  }

  // Bloques del detalle
  const bloqueIdentidad = (a) => ui.html`
    <div class="hoja identidad">
      <div class="identidad-rejilla">
        <figure class="identidad-placa">
          ${ui.placa(a.placaInventario, { sede: a.codSede })}
          <figcaption>Placa adherida al bien. Se fija al registrarlo y no cambia.</figcaption>
        </figure>
        <dl class="ficha ficha--renglones">
          <div><dt>Valor contable</dt><dd class="ficha-cifra">${ui.fmt.moneda(a.valorContable)}</dd></div>
          <div><dt>Estado</dt><dd>${ui.estadoActivo(a.estado)}</dd></div>
          <div><dt>Código UNSPSC</dt><dd class="mono">${a.codUnspsc}</dd></div>
          <div><dt>Sede</dt><dd class="mono">${a.codSede}</dd></div>
          <div><dt>NIT universidad</dt><dd class="mono">${a.nitUniv}</dd></div>
        </dl>
      </div>
    </div>`;

  function bloquePrestamo(a, p) {
    if (!p) {
      const ultimo = (a.prestamos || []).filter((x) => x.estado === 'DEVUELTO' && x.fechaDevolucionReal).sort(desc('fechaDevolucionReal'))[0];
      const situacion = {
        ACTIVO: 'Está disponible para prestar.',
        MANTENIMIENTO: 'Está en mantenimiento.',
        BAJA: 'Está de baja: no admite préstamos nuevos.',
      }[a.estado] || '';
      const historia = ultimo
        ? `La última devolución fue el ${ui.fmt.fechaHora(ultimo.fechaDevolucionReal)}, documento ${ultimo.numDocumento}.`
        : (a.prestamos || []).length ? '' : 'Nunca ha salido en préstamo.';
      return ui.vacio({ titulo: 'Sin préstamo vigente', texto: `${situacion} ${historia}`.trim() });
    }
    const pl = plazo(p);
    return ui.html`
      <div class="hoja prestamo${pl.vencido ? ' prestamo--vencido' : ''}">
        <div class="prestamo-cabeza">
          <div class="prestamo-custodio">
            <span class="etiqueta">Custodio</span>
            <span class="prestamo-doc">${p.numDocumento}</span>
            <span class="pequeno tenue">Documento de quien tiene el bien</span>
          </div>
          <div class="prestamo-sello">${ui.selloPrestamo(p)}</div>
        </div>
        <dl class="ficha prestamo-ficha">
          <div><dt>Salida</dt><dd>${ui.fmt.fechaHora(p.fechaSalida)}<span class="dd-nota">${ui.fmt.relativa(p.fechaSalida)}</span></dd></div>
          <div><dt>Devolución pactada</dt><dd>${ui.fmt.fechaHora(p.fechaDevolucionPactada)}</dd></div>
          <div class="prestamo-plazo"><dt>${pl.etiqueta}</dt><dd>${pl.texto}</dd></div>
        </dl>
        <div class="prestamo-pie">
          <span class="prestamo-id" title="Identificador del préstamo">Préstamo <span class="mono">${p.idPrestamo}</span></span>
          <button type="button" class="boton boton--primario" data-accion="devolver">${ui.icono('devolver')}<span>Registrar devolución</span></button>
        </div>
      </div>`;
  }

  function bloqueHistorial(a) {
    const lista = (a.prestamos || []).slice().sort(desc('fechaSalida'));
    return ui.html`
      <div class="tabla-marco">
        <table class="tabla">
          <thead><tr>
            <th scope="col">Salida y plazo</th><th scope="col">Custodio</th>
            <th scope="col">Devuelto</th><th scope="col">Estado</th>
          </tr></thead>
          <tbody>${lista.map((p) => ui.html`
            <tr>
              <td class="min"><span class="mono">${ui.fmt.registro(p.fechaSalida)}</span><span class="sub">hasta <span class="mono">${ui.fmt.registro(p.fechaDevolucionPactada)}</span></span></td>
              <td><span class="mono">${p.numDocumento}</span></td>
              <td class="min">${p.fechaDevolucionReal
                ? ui.html`<span class="mono">${ui.fmt.registro(p.fechaDevolucionReal)}</span><span class="sub">${puntualidad(p)}</span>`
                : ui.html`<span class="guion" aria-hidden="true">—</span><span class="sr">Sin devolver</span>`}</td>
              <td class="min">${ui.estadoPrestamo(p)}</td>
            </tr>`)}</tbody>
        </table>
      </div>`;
  }

  function renglonPendiente() {
    if (!esperando) return '';
    return ui.html`
      <li class="kardex-pendiente" data-tipo="${esperando.tipo}">
        <time>${esperando.agotado ? 'pendiente' : 'en camino'}</time>
        <span class="hito" aria-hidden="true"></span>
        <div class="texto">
          <strong class="mov-tipo">${ui.TIPO_MOVIMIENTO[esperando.tipo]}</strong>
          <span class="mov-doc">${esperando.agotado
            ? `Inventario aún no consume ${esperando.evento}. Puede tardar un poco más.`
            : `Inventario la asienta al consumir ${esperando.evento} de Kafka.`}</span>
          ${esperando.agotado ? ui.html`<button type="button" class="boton boton--sm kardex-recargar" data-accion="recargar-kardex">Volver a consultar</button>` : ''}
        </div>
      </li>`;
  }

  function bloqueKardex(a) {
    const movs = (a.movimientos || []).slice().sort(ordenMov);
    if (!movs.length && !esperando) {
      return ui.vacio({ titulo: 'Kardex sin movimientos', texto: 'Inventario todavía no registra movimientos de este activo.' });
    }
    const visibles = movs.slice(0, MAX_KARDEX);
    return ui.html`
      <div class="hoja kardex">
        <ol class="cronologia">
          ${renglonPendiente()}
          ${visibles.map((m) => ui.html`
            <li data-tipo="${m.tipoMovimiento}">
              <time datetime="${m.fechaMovimiento}">${dosRenglones(m.fechaMovimiento)}</time>
              <span class="hito" aria-hidden="true"></span>
              <div class="texto">
                <strong class="mov-tipo">${ui.TIPO_MOVIMIENTO[m.tipoMovimiento] || m.tipoMovimiento}</strong>
                <span class="mov-doc">Documento <span class="mono">${m.numDocumento}</span></span>
              </div>
            </li>`)}
        </ol>
        ${movs.length > MAX_KARDEX
          ? ui.html`<p class="kardex-mas">Se ven los ${MAX_KARDEX} más recientes de ${movs.length}. <a href="inventario.html?placa=${enc(a.placaInventario)}">Kardex completo</a></p>`
          : ''}
      </div>`;
  }

  function bloqueCustodios(a) {
    const lista = (a.asignaciones || []).slice().sort(desc('fechaHoraActa'));
    if (!lista.length) {
      return ui.vacio({ titulo: 'Sin custodio asignado', texto: 'Nadie ha firmado acta de entrega por este activo.' });
    }
    return ui.html`
      <div class="hoja">
        <ul class="custodios">
          ${lista.map((x) => ui.html`
            <li>
              <span class="custodio-doc mono">${x.numDocumento}</span>
              <span class="custodio-rel">${ui.fmt.relativa(x.fechaHoraActa)}</span>
              <span class="custodio-acta">Acta del <span class="mono">${ui.fmt.registro(x.fechaHoraActa)}</span></span>
            </li>`)}
        </ul>
      </div>`;
  }

  // Pintado
  function pintar(a) {
    activo = a;
    document.title = `${a.placaInventario} · ${a.descripcion} · ETER Patrimonio`;
    const baja = a.estado === 'BAJA';
    const vigente = a.prestamoVigente && a.prestamoVigente.estado === 'VIGENTE' ? a.prestamoVigente : null;
    const urlPrestamo = `prestamos.html?nuevo=${enc(a.placaInventario)}`;
    let nuevoPrestamo = '';
    if (!vigente && !baja) {
      nuevoPrestamo = sesion.esAdmin()
        ? ui.html`<a class="boton boton--primario" href="${urlPrestamo}">${ui.icono('prestamos')}<span>Nuevo préstamo</span></a>`
        : ui.html`<button type="button" class="boton boton--primario" disabled title="${ui.SIN_PERMISO}">${ui.icono('prestamos')}<span>Nuevo préstamo</span></button>`;
    }

    el.cabecera.innerHTML = ui.html`
      <div class="cabecera-cuerpo">
        ${folio(a.placaInventario)}
        <h1 class="titulo">${a.descripcion}</h1>
        <p class="cabecera-estado">${ui.estadoActivo(a.estado)}</p>
      </div>
      <div class="cabecera-acciones">
        <button type="button" class="boton" data-accion="editar">${ui.icono('editar')}<span>Editar</span></button>
        <button type="button" class="boton boton--peligro" data-accion="baja"${ui.crudo(baja ? ' disabled title="Este activo ya está de baja."' : '')}>${ui.icono('baja')}<span>Dar de baja</span></button>
        ${nuevoPrestamo}
      </div>`;

    el.detalle.className = 'columnas detalle';
    el.detalle.innerHTML = ui.html`
      <div class="pila detalle-principal">
        <section class="seccion" aria-labelledby="t-ficha">
          <h2 class="seccion-titulo" id="t-ficha">Ficha del bien</h2>
          ${bloqueIdentidad(a)}
        </section>
        <section class="seccion" aria-labelledby="t-prestamo">
          <h2 class="seccion-titulo" id="t-prestamo">Préstamo vigente</h2>
          ${bloquePrestamo(a, vigente)}
        </section>
        ${(a.prestamos || []).length ? ui.html`
        <section class="seccion" aria-labelledby="t-historial">
          <h2 class="seccion-titulo" id="t-historial">Historial de préstamos <span class="seccion-cuenta">${a.prestamos.length}</span></h2>
          ${bloqueHistorial(a)}
        </section>` : ''}
      </div>
      <div class="pila detalle-lateral">
        <section class="seccion" aria-labelledby="t-kardex">
          <div class="seccion-cabeza">
            <h2 class="seccion-titulo" id="t-kardex">Kardex del activo <span class="seccion-cuenta">${(a.movimientos || []).length}</span></h2>
            <a class="enlace-seccion" href="inventario.html?placa=${enc(a.placaInventario)}">Ver en inventario</a>
          </div>
          ${bloqueKardex(a)}
        </section>
        <section class="seccion" aria-labelledby="t-custodios">
          <div class="seccion-cabeza">
            <h2 class="seccion-titulo" id="t-custodios">Custodios asignados <span class="seccion-cuenta">${(a.asignaciones || []).length}</span></h2>
            <button type="button" class="boton boton--sm" data-accion="asignar">${ui.icono('usuario')}<span>Asignar a un custodio</span></button>
          </div>
          ${bloqueCustodios(a)}
        </section>
      </div>`;

    ui.soloAdmin(
      [...el.cabecera.querySelectorAll('button[data-accion]')],
      [...el.detalle.querySelectorAll('[data-accion="devolver"], [data-accion="asignar"]')],
    );
  }

  // Seguimiento de eventos
  function programarSeguimiento() {
    clearTimeout(temporizador);
    temporizador = setTimeout(() => {
      if (!esperando) return;
      esperando.intentos += 1;
      cargar();
    }, ESPERA_MS);
  }

  /** Espera a que el movimiento llegue al kardex. */
  function esperar(tipo, evento) {
    esperando = { tipo, evento, antes: contar(activo, tipo), intentos: 0, agotado: false };
  }

  async function cargar({ inicial = false } = {}) {
    if (inicial) pintarCargando();
    let a;
    try {
      a = await api.activo(placa);
    } catch (e) {
      if (inicial || !activo) pintarError(e);
      else ui.error(e);
      return;
    }
    if (!a) { pintarInexistente(); return; }

    if (esperando) {
      if (contar(a, esperando.tipo) > esperando.antes) {
        ui.toast(`Kardex al día: ${FRASE_MOV[esperando.tipo] || esperando.tipo} ya quedó asentada por inventario.`);
        esperando = null;
        clearTimeout(temporizador);
      } else if (esperando.intentos >= INTENTOS) {
        esperando.agotado = true;
      }
    }
    pintar(a);
    if (esperando && !esperando.agotado) programarSeguimiento();
  }

  // Formularios
  function prepararForm(form, alerta, boton) {
    form.reset();
    ui.limpiarErrores(form);
    alerta.hidden = true;
    alerta.innerHTML = '';
    ui.ocupado(boton, false);
  }

  /** Envía una mutación desde un diálogo y muestra los errores. */
  async function enviar({ form, alerta, boton, dlg, conflicto }, mutacion, exito) {
    ui.limpiarErrores(form);
    alerta.hidden = true;
    alerta.innerHTML = '';
    ui.ocupado(boton, true);
    let r;
    try {
      r = await mutacion();
    } catch (e) {
      ui.ocupado(boton, false);
      if (e?.code === 'UNAUTHENTICATED') { ui.cerrar(dlg); ui.error(e); return; }
      if (e?.code === 'BAD_USER_INPUT') ui.marcarErrores(form, e.errores);
      if (e?.code === 'CONFLICT' && conflicto) ui.marcarErrores(form, [conflicto]);
      alerta.innerHTML = ui.alerta(e);
      alerta.hidden = false;
      const invalido = form.querySelector('[aria-invalid="true"]');
      if (invalido) invalido.focus();
      else alerta.scrollIntoView({ block: 'nearest' });
      return;
    }
    ui.ocupado(boton, false);
    ui.cerrar(dlg);
    await exito(r);
  }

  function vistaValor() {
    const v = el.valorEditar.value;
    const n = Number(v);
    el.vistaEditar.textContent = v !== '' && Number.isFinite(n) && n >= 0 ? `Queda en ${ui.fmt.moneda(n)}.` : '';
  }

  async function cargarSedes() {
    if (sedesPedidas) return;
    sedesPedidas = true;
    try {
      const lista = await api.activos();
      const sedes = [...new Set(lista.map((x) => x.codSede))].sort((x, y) => x.localeCompare(y, 'es', { numeric: true }));
      $('#lista-sedes').innerHTML = ui.html`${sedes.map((s) => ui.html`<option value="${s}"></option>`)}`;
    } catch { sedesPedidas = false; /* sin sugerencias */ }
  }

  function abrirEditar() {
    const f = el.formEditar;
    prepararForm(f, el.alertaEditar, el.enviarEditar);
    $('#e-placa').textContent = activo.placaInventario;
    const opciones = activo.estado === 'BAJA' ? ['ACTIVO', 'MANTENIMIENTO', 'BAJA'] : ['ACTIVO', 'MANTENIMIENTO'];
    f.elements.estado.innerHTML = ui.html`${opciones.map((e) => ui.html`<option value="${e}">${ui.ESTADO_ACTIVO[e][0]}</option>`)}`;
    f.elements.descripcion.value = activo.descripcion;
    f.elements.codUnspsc.value = activo.codUnspsc;
    f.elements.codSede.value = activo.codSede;
    f.elements.valorContable.value = activo.valorContable;
    f.elements.estado.value = activo.estado;
    $('#e-estado-ayuda').textContent = activo.estado === 'BAJA'
      ? 'Está de baja. Cambiar el estado aquí no agrega movimientos al kardex.'
      : 'Para retirarlo usa «Dar de baja»: así inventario asienta la BAJA en el kardex.';
    vistaValor();
    ui.abrir(el.dlgEditar);
    cargarSedes();
  }

  el.formEditar.addEventListener('submit', (ev) => {
    ev.preventDefault();
    const d = ui.datosForm(el.formEditar);
    const input = {
      descripcion: d.descripcion,
      codUnspsc: d.codUnspsc,
      codSede: d.codSede,
      valorContable: Number(d.valorContable),
      estado: d.estado,
    };
    const p = activo.placaInventario;
    enviar(
      { form: el.formEditar, alerta: el.alertaEditar, boton: el.enviarEditar, dlg: el.dlgEditar },
      () => api.actualizarActivo(p, input),
      async () => { ui.toast(`Cambios guardados en ${p}.`); await cargar(); },
    );
  });
  el.valorEditar.addEventListener('input', vistaValor);

  function abrirDevolucion() {
    const p = activo.prestamoVigente;
    if (!p) return;
    const f = el.formDevolucion;
    prepararForm(f, el.alertaDevolucion, el.enviarDevolucion);
    el.resumenDevolucion.innerHTML = ui.html`
      <div><dt>Activo</dt><dd class="mono">${activo.placaInventario}</dd></div>
      <div><dt>Custodio</dt><dd class="mono">${p.numDocumento}</dd></div>
      <div><dt>Pactada</dt><dd class="${p.vencido ? 'texto-peligro' : ''}">${ui.fmt.fechaHora(p.fechaDevolucionPactada)}</dd></div>`;
    const ahora = ui.fechaInput(0);
    const campo = f.elements.fechaDevolucionReal;
    campo.value = ahora;
    campo.max = ahora;
    campo.min = ui.aInputLocal(p.fechaSalida);
    ui.abrir(el.dlgDevolucion);
  }

  el.formDevolucion.addEventListener('submit', (ev) => {
    ev.preventDefault();
    const p = activo.prestamoVigente;
    if (!p) return;
    const fecha = ui.aLocalDateTime(ui.datosForm(el.formDevolucion).fechaDevolucionReal);
    if (fecha && new Date(fecha) > new Date()) {
      ui.marcarErrores(el.formDevolucion, [{ campo: 'fechaDevolucionReal', mensaje: 'No puede ser una fecha futura.' }]);
      return;
    }
    enviar(
      { form: el.formDevolucion, alerta: el.alertaDevolucion, boton: el.enviarDevolucion, dlg: el.dlgDevolucion },
      () => api.registrarDevolucion(p.idPrestamo, fecha),
      async () => {
        esperar('DEVOLUCION', 'PRESTAMO_DEVUELTO');
        ui.toast(`Devolución registrada. La DEVOLUCION llega al kardex y el custodio ${p.numDocumento} recibe la notificación en unos segundos (evento PRESTAMO_DEVUELTO vía Kafka).`, { ms: 8000 });
        await cargar();
      },
    );
  });

  function abrirAsignar() {
    const f = el.formAsignar;
    prepararForm(f, el.alertaAsignar, el.enviarAsignar);
    $('#a-placa').textContent = activo.placaInventario;
    f.elements.fechaHoraActa.value = ui.fechaInput(0);
    ui.abrir(el.dlgAsignar);
  }

  el.formAsignar.addEventListener('submit', (ev) => {
    ev.preventDefault();
    const d = ui.datosForm(el.formAsignar);
    const input = {
      placaInventario: activo.placaInventario,
      numDocumento: d.numDocumento,
      fechaHoraActa: ui.aLocalDateTime(d.fechaHoraActa),
    };
    enviar(
      {
        form: el.formAsignar, alerta: el.alertaAsignar, boton: el.enviarAsignar, dlg: el.dlgAsignar,
        conflicto: { campo: 'numDocumento', mensaje: 'Este documento ya tiene el activo asignado.' },
      },
      () => api.crearAsignacion(input),
      async () => { ui.toast(`${input.placaInventario} asignado al documento ${input.numDocumento}. El acta queda en inventario.`); await cargar(); },
    );
  });

  async function darDeBaja() {
    const a = activo;
    const p = a.prestamoVigente && a.prestamoVigente.estado === 'VIGENTE' ? a.prestamoVigente : null;
    const ok = await ui.confirmar({
      titulo: `Dar de baja ${a.placaInventario}`,
      texto: `${a.descripcion} pasa a estado BAJA y deja de prestarse. Activos publica ACTIVO_DADO_DE_BAJA e inventario asienta la BAJA en el kardex.${
        p ? ` Ojo: tiene un préstamo vigente con el documento ${p.numDocumento}, que sigue abierto hasta que registres la devolución.` : ''}`,
      accion: 'Dar de baja',
      peligro: true,
    });
    if (!ok) return;
    const boton = el.cabecera.querySelector('[data-accion="baja"]');
    if (boton) ui.ocupado(boton, true);
    try {
      await api.darDeBajaActivo(a.placaInventario);
    } catch (e) {
      if (boton) ui.ocupado(boton, false);
      ui.error(e);
      return;
    }
    esperar('BAJA', 'ACTIVO_DADO_DE_BAJA');
    ui.toast(`${a.placaInventario} dado de baja. La BAJA aparece en el kardex en unos segundos (evento ACTIVO_DADO_DE_BAJA vía Kafka).`, { ms: 8000 });
    await cargar();
  }

  // Eventos
  document.addEventListener('click', (ev) => {
    const cerrar = ev.target.closest('[data-cerrar]');
    if (cerrar) { ui.cerrar(cerrar.closest('dialog')); return; }
    const b = ev.target.closest('[data-accion]');
    if (!b || b.disabled) return;
    const accion = b.dataset.accion;
    if (accion === 'reintentar') { cargar({ inicial: true }); return; }
    if (!activo) return;
    switch (accion) {
      case 'editar': abrirEditar(); break;
      case 'baja': darDeBaja(); break;
      case 'devolver': abrirDevolucion(); break;
      case 'asignar': abrirAsignar(); break;
      case 'recargar-kardex':
        if (esperando) { esperando.intentos = 0; esperando.agotado = false; }
        cargar();
        break;
      default: break;
    }
  });

  // Arranque
  if (!placa) { pintarInexistente(); return; }
  if (recienCreado) {
    esperando = { tipo: 'ALTA', evento: 'ACTIVO_CREADO', antes: 0, intentos: 0, agotado: false };
    ui.toast(`Activo ${placa} registrado. El alta aparece en el kardex en unos segundos (evento ACTIVO_CREADO vía Kafka).`, { ms: 8000 });
    quitarParametro('creado');
  }
  cargar({ inicial: true });
})();
