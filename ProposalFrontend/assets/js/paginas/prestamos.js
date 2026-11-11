/*
 * ETER · Préstamos (prestamos.html)
 *
 * Cifras del día, calendario de vigentes, registro filtrable, nuevo préstamo
 * y registro de devolución. Todo pasa por ETER.api.
 *
 * Parámetros de URL:
 *   ?filtro=vigentes|vencidos|devueltos|todos
 *   ?placa=AF-2026-0001   prefiltra el buscador (sin ?filtro muestra todos)
 *   ?nuevo= / ?nuevo=AF-… abre el diálogo de nuevo préstamo, con la placa si viene
 *   ?devolver=ID          abre la devolución de ese préstamo
 */
(function () {
  'use strict';
  if (!ETER.ui.montar({ pagina: 'prestamos' })) return;

  const { ui, api, sesion } = ETER;
  const { html, fmt } = ui;

  const DIA = 86400000;
  const RX_PLACA = /^[A-Za-z0-9]+(-[A-Za-z0-9]+)*$/;
  const RX_DOC = /^[A-Za-z0-9]{5,20}$/;
  const MESES = ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'sep', 'oct', 'nov', 'dic'];
  const FILTROS = {
    vigentes: { nombre: 'vigentes', uno: 'vigente', pasa: (p) => p.estado === 'VIGENTE' },
    vencidos: { nombre: 'vencidos', uno: 'vencido', pasa: (p) => p.vencido },
    devueltos: { nombre: 'devueltos', uno: 'devuelto', pasa: (p) => p.estado === 'DEVUELTO' },
    todos: { nombre: 'registrados', uno: 'registrado', pasa: () => true },
  };

  const $ = (s) => document.querySelector(s);
  const el = {
    nuevo: $('#btn-nuevo'),
    cifras: $('#cifras'),
    cal: $('#calendario'),
    calEscala: $('#cal-escala'),
    segmentos: $('#segmentos'),
    buscar: $('#buscar'),
    conteo: $('#conteo'),
    registro: $('#registro'),
  };
  const paginas = ui.paginador($('#paginador'), { alCambiar: () => pintarRegistro() });
  const nuevo = {
    dlg: $('#dlg-nuevo'), form: $('#form-nuevo'), alerta: $('#np-alerta'),
    lista: $('#np-activos'), custodios: $('#np-custodios'), estadoLista: $('#np-lista'),
    activo: $('#np-activo'), correo: $('#np-correo'), plazo: $('#np-plazo'),
  };
  const dev = {
    dlg: $('#dlg-devolver'), form: $('#form-devolver'), alerta: $('#dev-alerta'),
    sello: $('#dev-sello'), resumen: $('#dev-resumen'), cumple: $('#dev-cumple'),
  };

  const filtroUrl = ui.param('filtro');
  const placaUrl = (ui.param('placa') || '').trim();
  const estado = {
    prestamos: [],
    cargado: false,
    filtro: filtroUrl && Object.hasOwn(FILTROS, filtroUrl) ? filtroUrl : placaUrl ? 'todos' : 'vigentes',
    q: placaUrl,
    activos: null,        // Map placa -> activo en estado ACTIVO (se trae al abrir el diálogo)
    turnoActivos: 0,
    reciente: null,       // idPrestamo recién creado o devuelto, para resaltarlo
    calCentrado: false,
    enDevolucion: null,   // préstamo abierto en el diálogo de devolución
  };

  // ── Utilidades ────────────────────────────────────────────────────────────
  const ms = (iso) => (iso ? new Date(iso).getTime() : NaN);
  const inicioDia = (t) => { const d = new Date(t); d.setHours(0, 0, 0, 0); return d.getTime(); };
  const sumarDias = (t, n) => { const d = new Date(t); d.setDate(d.getDate() + n); return d.getTime(); };
  const dos = (n) => String(n).padStart(2, '0');
  const hora = (t) => { const d = new Date(t); return `${dos(d.getHours())}:${dos(d.getMinutes())}`; };
  const plural = (n, uno, varios) => `${n} ${n === 1 ? uno : varios}`;
  const normal = (s) => String(s ?? '').normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase();
  const esVencido = (p) => p.estado === 'VIGENTE' && (p.vencido === true || ms(p.fechaDevolucionPactada) < Date.now());
  const porPactada = (a, b) => ms(a.fechaDevolucionPactada) - ms(b.fechaDevolucionPactada);
  const consulta = () => normal(estado.q.trim());
  const coincide = (p, q) => !q || [p.placaInventario, p.numDocumento, p.activo?.descripcion].some((v) => normal(v).includes(q));

  /** Cuánto falta (o cuánto se pasó) de la devolución pactada de un préstamo vigente. */
  function plazo(p) {
    const n = fmt.diasHasta(p.fechaDevolucionPactada);
    if (n == null) return { texto: 'sin fecha pactada', corto: '', tono: '' };
    const h = hora(p.fechaDevolucionPactada);
    if (p.vencido) {
      const d = Math.max(0, -n);
      if (d === 0) return { texto: `venció hoy a las ${h}`, corto: 'venció hoy', tono: 'mora' };
      return { texto: `vencido hace ${plural(d, 'día', 'días')}`, corto: `vencido hace ${d} d`, tono: 'mora' };
    }
    if (n <= 0) return { texto: `vence hoy a las ${h}`, corto: `vence hoy, ${h}`, tono: 'hoy' };
    if (n === 1) return { texto: `vence mañana a las ${h}`, corto: 'vence mañana', tono: 'pronto' };
    return { texto: `vence en ${n} días`, corto: `vence en ${n} d`, tono: '' };
  }

  /** Devolución real frente a la pactada: a tiempo o cuánto tarde. */
  function cumplimiento(real, pactada) {
    const r = ms(real), pz = ms(pactada);
    if (Number.isNaN(r) || Number.isNaN(pz)) return null;
    if (r <= pz) return { texto: 'a tiempo', tono: '' };
    const dias = Math.round((inicioDia(r) - inicioDia(pz)) / DIA);
    if (dias >= 1) return { texto: `${plural(dias, 'día', 'días')} tarde`, tono: 'tarde' };
    return { texto: `${plural(Math.max(1, Math.round((r - pz) / 3600000)), 'hora', 'horas')} tarde`, tono: 'tarde' };
  }

  function sincronizarUrl(quitar = []) {
    try {
      const u = new URL(location.href);
      const q = estado.q.trim();
      if (estado.filtro === 'vigentes' && !q) u.searchParams.delete('filtro');
      else u.searchParams.set('filtro', estado.filtro);
      if (q) u.searchParams.set('placa', q); else u.searchParams.delete('placa');
      quitar.forEach((k) => u.searchParams.delete(k));
      history.replaceState(null, '', u);
    } catch { /* desde file:// algunos navegadores niegan replaceState */ }
  }

  // ── Carga ─────────────────────────────────────────────────────────────────
  const CABEZA = html`<thead><tr>
    <th scope="col">Placa</th><th scope="col">Activo</th><th scope="col">Custodio</th><th scope="col">Salida</th>
    <th scope="col">Devolución pactada</th><th scope="col">Devolución real</th><th scope="col">Estado</th>
    <th scope="col"><span class="sr">Acción</span></th>
  </tr></thead>`;

  async function cargar({ primera = false } = {}) {
    if (primera) {
      estado.cargado = false;
      el.cifras.innerHTML = html`${[0, 1, 2].map(() => html`
        <div class="cifra" aria-hidden="true">
          <span class="cifra-valor"><span class="esqueleto" style="width:34px;height:30px"></span></span>
          <span class="esqueleto" style="width:60%"></span>
          <span class="esqueleto" style="width:85%"></span>
        </div>`)}`;
      el.cal.innerHTML = html`<div class="hoja-cuerpo">${ui.cargandoBloque(4)}</div>`;
      el.registro.innerHTML = html`<div class="tabla-marco"><table class="tabla tabla--prestamos">${CABEZA}<tbody>${ui.cargandoFilas(8, 4)}</tbody></table></div>`;
      el.conteo.textContent = 'Cargando préstamos…';
      pintarSegmentos();
    }
    try {
      const lista = await api.prestamos();
      estado.prestamos = (lista || []).map((p) => ({ ...p, vencido: esVencido(p) }));
      estado.cargado = true;
    } catch (e) {
      if (estado.cargado) { ui.error(e); return false; }
      pintarCifras();
      el.cal.innerHTML = html`<div class="hoja-cuerpo"><p class="pequeno tenue">Sin datos de préstamos no hay calendario que dibujar.</p></div>`;
      el.calEscala.textContent = '';
      el.conteo.textContent = '';
      ui.error(e, { en: el.registro });
      el.registro.insertAdjacentHTML('beforeend', String(html`<p class="reintentar"><button type="button" class="boton boton--sm" data-reintentar>Reintentar</button></p>`));
      return false;
    }
    pintarCifras();
    pintarCalendario();
    pintarSegmentos();
    pintarRegistro();
    return true;
  }

  // ── Cifras ────────────────────────────────────────────────────────────────
  const cifra = (valor, etiqueta, nota, peligro = false) => html`
    <div class="cifra${peligro ? ' cifra--peligro' : ''}">
      <span class="cifra-valor">${valor}</span>
      <span class="cifra-etiqueta">${etiqueta}</span>
      <span class="cifra-nota">${nota}</span>
    </div>`;

  function pintarCifras() {
    if (!estado.cargado) {
      el.cifras.innerHTML = html`${cifra('—', 'Vigentes', 'Sin datos')}${cifra('—', 'Vencidos', 'Sin datos')}${cifra('—', 'Devueltos · 30 días', 'Sin datos')}`;
      return;
    }
    const ps = estado.prestamos;
    const vigentes = ps.filter((p) => p.estado === 'VIGENTE');
    const vencidos = vigentes.filter((p) => p.vencido).sort(porPactada);
    const proximo = vigentes.filter((p) => !p.vencido).sort(porPactada)[0];
    const desde = sumarDias(Date.now(), -30);
    const devueltos = ps.filter((p) => p.estado === 'DEVUELTO' && ms(p.fechaDevolucionReal) >= desde);
    const tarde = devueltos.filter((p) => cumplimiento(p.fechaDevolucionReal, p.fechaDevolucionPactada)?.tono === 'tarde').length;

    el.cifras.innerHTML = html`
      ${cifra(vigentes.length, 'Vigentes', proximo
        ? html`Próxima devolución: <span class="mono">${proximo.placaInventario}</span>, ${fmt.fechaHora(proximo.fechaDevolucionPactada)}`
        : vigentes.length ? 'Todos pasaron de su fecha pactada' : 'Ningún activo está prestado')}
      ${cifra(vencidos.length, 'Vencidos', vencidos.length
        ? html`El más atrasado: <span class="mono">${vencidos[0].placaInventario}</span>, ${plazo(vencidos[0]).texto}`
        : 'Ninguno pasó de la fecha pactada', vencidos.length > 0)}
      ${cifra(devueltos.length, 'Devueltos · 30 días', devueltos.length
        ? `${devueltos.length - tarde} a tiempo, ${tarde} con retraso`
        : `Sin devoluciones desde el ${fmt.fecha(desde)}`)}`;
  }

  // ── Calendario de vigentes ────────────────────────────────────────────────
  function pintarCalendario() {
    const anterior = el.cal.querySelector('.cal')?.scrollLeft;
    const vigentes = estado.prestamos.filter((p) => p.estado === 'VIGENTE').sort(porPactada);
    if (!vigentes.length) {
      el.cal.innerHTML = ui.vacio({
        titulo: 'Sin préstamos vigentes',
        texto: 'Ningún activo está fuera en préstamo. Cada salida que registres aparece aquí como una barra desde la salida hasta la devolución pactada.',
        accion: html`<button type="button" class="boton boton--sm" data-nuevo>Nuevo préstamo</button>`,
      });
      el.calEscala.textContent = '';
      ui.soloAdmin([...el.cal.querySelectorAll('[data-nuevo]')]);
      return;
    }

    // Escala: al menos una semana atrás y dos adelante; se estira para que quepan
    // las salidas y las fechas pactadas (con aire para el rótulo del plazo).
    const ahora = Date.now();
    const hoy0 = inicioDia(ahora);
    let ini = sumarDias(hoy0, -7);
    let fin = sumarDias(hoy0, 15);
    for (const p of vigentes) {
      const s = ms(p.fechaSalida), e = ms(p.fechaDevolucionPactada);
      if (!Number.isNaN(s)) ini = Math.min(ini, sumarDias(inicioDia(s), -2));
      if (!Number.isNaN(e)) fin = Math.max(fin, sumarDias(inicioDia(e), 7));
    }
    ini = Math.max(ini, sumarDias(hoy0, -90));
    fin = Math.min(fin, sumarDias(hoy0, 120));

    const dias = [];
    for (let t = ini; t < fin; t = sumarDias(t, 1)) dias.push(t);
    const total = fin - ini;
    const pct = (t) => Math.min(100, Math.max(0, ((t - ini) / total) * 100));
    const n = (x) => x.toFixed(3);
    const pHoy = pct(ahora);

    const columnas = [], numeros = [], meses = [];
    dias.forEach((t, i) => {
      const d = new Date(t), dow = d.getDay();
      const izq = pct(t), ancho = pct(dias[i + 1] ?? fin) - izq;
      const finde = dow === 0 || dow === 6, lunes = dow === 1, hoy = t === hoy0;
      columnas.push(html`<span class="cal-dia${finde ? ' cal-dia--finde' : ''}${lunes ? ' cal-dia--lunes' : ''}${hoy ? ' cal-dia--hoy' : ''}" style="left:${n(izq)}%;width:${n(ancho)}%"></span>`);
      numeros.push(html`<span class="cal-num${finde ? ' cal-num--finde' : ''}${lunes ? ' cal-num--lunes' : ''}${hoy ? ' cal-num--hoy' : ''}" style="left:${n(izq)}%;width:${n(ancho)}%"><b>${d.getDate()}</b></span>`);
      if (i === 0 || d.getDate() === 1) meses.push({ i, izq, d });
    });
    // Si el primer rótulo de mes queda pegado al siguiente, sobra.
    if (meses.length > 1 && meses[1].i - meses[0].i < 4) meses.shift();
    const rotulosMes = meses.map(({ izq, d }, k) => html`<span class="cal-mes" style="left:${n(izq)}%">${MESES[d.getMonth()]}${k === 0 || d.getMonth() === 0 ? ` ${d.getFullYear()}` : ''}</span>`);

    const filas = vigentes.map((p) => {
      const s = ms(p.fechaSalida), e = ms(p.fechaDevolucionPactada);
      const pS = pct(s), pE = pct(e);
      const hechoHasta = Math.min(pHoy, pE);
      const pz = plazo(p);
      const desc = p.activo?.descripcion || 'Activo sin datos';
      const clasesPlazo = `cal-plazo${s < ini ? ' cal-plazo--corte-izq' : ''}${e > fin ? ' cal-plazo--corte-der' : ''}`;
      const finBarra = p.vencido ? Math.max(pE, pHoy) : pE;
      let resto = '';
      if (pz.corto && finBarra <= 84) {
        resto = html`<span class="cal-resto cal-resto--${pz.tono || 'normal'}" style="left:calc(${n(finBarra)}% + 8px)">${pz.corto}</span>`;
      } else if (pz.corto && pS >= 16) {
        resto = html`<span class="cal-resto cal-resto--${pz.tono || 'normal'}" style="right:calc(${n(100 - pS)}% + 8px)">${pz.corto}</span>`;
      }
      return html`
        <li class="cal-fila${p.vencido ? ' cal-fila--mora' : ''}">
          <div class="cal-etiqueta" title="${desc}">
            ${ui.enlaceActivo(p.placaInventario)}
            <span class="cal-doc">${p.numDocumento}</span>
          </div>
          <div class="cal-pista" title="${p.placaInventario} · ${desc}&#10;Salida ${fmt.fechaHora(p.fechaSalida)}&#10;Devolución pactada ${fmt.fechaHora(p.fechaDevolucionPactada)}">
            <span class="sr">${desc}, custodio ${p.numDocumento}. Salida ${fmt.fechaHora(p.fechaSalida)}; devolución pactada ${fmt.fechaHora(p.fechaDevolucionPactada)}: ${pz.texto}.</span>
            <span class="${clasesPlazo}" style="left:${n(pS)}%;width:${n(Math.max(0, pE - pS))}%"></span>
            ${hechoHasta > pS ? html`<span class="cal-hecho" style="left:${n(pS)}%;width:${n(hechoHasta - pS)}%"></span>` : ''}
            ${p.vencido && pHoy > pE ? html`<span class="cal-mora" style="left:${n(pE)}%;width:${n(pHoy - pE)}%"></span>` : ''}
            ${resto}
          </div>
        </li>`;
    });

    const bordeHoy = pHoy < 7 ? ' cal-hoy--izq' : pHoy > 93 ? ' cal-hoy--der' : '';
    el.cal.innerHTML = html`
      <div class="cal" style="--dias:${dias.length}">
        <div class="cal-lienzo">
          <div class="cal-rejilla" aria-hidden="true">${columnas}</div>
          <span class="cal-hoy${bordeHoy}" data-etiqueta="Hoy ${hora(ahora)}" aria-hidden="true"
                style="left:calc(var(--cal-et) + (100% - var(--cal-et)) * ${(pHoy / 100).toFixed(5)})"></span>
          <div class="cal-cabeza" aria-hidden="true">
            <div class="cal-esquina">Placa · custodio</div>
            <div class="cal-escala">${rotulosMes}${numeros}</div>
          </div>
          <ol class="cal-filas" aria-label="Préstamos vigentes, del más próximo a vencer al más lejano">${filas}</ol>
          <div class="cal-pie" aria-hidden="true"><div class="cal-esquina"></div><div></div></div>
        </div>
      </div>`;
    el.calEscala.textContent = `Del ${fmt.fecha(ini)} al ${fmt.fecha(sumarDias(fin, -1))} · ${plural(dias.length, 'día', 'días')}. Filas por devolución pactada.`;

    // En pantallas angostas el calendario se desplaza: la primera vez deja hoy a un
    // tercio del ancho visible, para que se vea lo que falta de cada plazo.
    const caja = el.cal.querySelector('.cal');
    if (estado.calCentrado && anterior != null) {
      caja.scrollLeft = anterior;
    } else if (caja.scrollWidth > caja.clientWidth) {
      const et = caja.querySelector('.cal-esquina').offsetWidth;
      const pista = caja.scrollWidth - et;
      caja.scrollLeft = Math.max(0, (pista * pHoy) / 100 - (caja.clientWidth - et) * 0.35);
    }
    estado.calCentrado = true;
  }

  // ── Registro (segmentos, buscador y tabla) ────────────────────────────────
  function pintarSegmentos() {
    const q = consulta();
    const base = estado.prestamos.filter((p) => coincide(p, q));
    el.segmentos.querySelectorAll('button[data-filtro]').forEach((b) => {
      const f = b.dataset.filtro;
      const cuenta = b.querySelector('.cuenta');
      const total = base.filter(FILTROS[f].pasa).length;
      b.setAttribute('aria-pressed', String(f === estado.filtro));
      cuenta.textContent = estado.cargado ? String(total) : '';
      cuenta.classList.toggle('cuenta--mora', f === 'vencidos' && total > 0);
    });
  }

  function orden(a, b) {
    const va = a.estado === 'VIGENTE', vb = b.estado === 'VIGENTE';
    if (va !== vb) return va ? -1 : 1;
    if (va) return porPactada(a, b);
    return ms(b.fechaDevolucionReal || b.fechaSalida) - ms(a.fechaDevolucionReal || a.fechaSalida);
  }

  function fila(p) {
    const pz = p.estado === 'VIGENTE' ? plazo(p) : null;
    const cu = p.estado === 'DEVUELTO' ? cumplimiento(p.fechaDevolucionReal, p.fechaDevolucionPactada) : null;
    const reciente = p.idPrestamo === estado.reciente;
    return html`
      <tr data-id="${p.idPrestamo}"${ui.crudo(reciente ? ' class="fila-reciente"' : '')}>
        <td class="min">${ui.enlaceActivo(p.placaInventario)}</td>
        ${p.activo
          ? html`<td class="principal">${p.activo.descripcion}<span class="sub">Sede ${p.activo.codSede}</span></td>`
          : html`<td><span class="tenue">Sin datos del activo</span></td>`}
        <td class="min"><span class="mono">${p.numDocumento}</span></td>
        <td class="min"><span class="mono">${fmt.registro(p.fechaSalida)}</span></td>
        <td class="min">
          <span class="mono">${fmt.registro(p.fechaDevolucionPactada)}</span>
          ${pz ? html`<span class="plazo${pz.tono ? ` plazo--${pz.tono}` : ''}">${pz.texto}</span>` : ''}
        </td>
        <td class="min">
          ${p.fechaDevolucionReal ? html`<span class="mono">${fmt.registro(p.fechaDevolucionReal)}</span>` : html`<span class="tenue">—</span>`}
          ${cu ? html`<span class="plazo${cu.tono ? ` plazo--${cu.tono}` : ''}">${cu.texto}</span>` : ''}
        </td>
        <td class="min">${ui.estadoPrestamo(p)}</td>
        <td class="min der">${p.estado === 'VIGENTE'
          ? html`<button type="button" class="boton boton--sm" data-devolver="${p.idPrestamo}">${ui.icono('devolver')}<span>Registrar devolución</span></button>`
          : ''}</td>
      </tr>`;
  }

  function vacioRegistro(q) {
    if (q) {
      const otros = estado.filtro !== 'todos' && estado.prestamos.some((p) => coincide(p, consulta()));
      return ui.vacio({
        titulo: 'Sin coincidencias',
        texto: `Ningún préstamo ${FILTROS[estado.filtro].uno} coincide con «${estado.q.trim()}». Se busca por placa, documento del custodio o descripción del activo.`,
        accion: html`<span class="fila">
          ${otros ? html`<button type="button" class="boton boton--sm" data-ver-todos>Buscar en todos</button>` : ''}
          <button type="button" class="boton boton--sm boton--plano" data-limpiar>Limpiar búsqueda</button>
        </span>`,
      });
    }
    const textos = {
      vigentes: ['No hay préstamos vigentes', 'Todos los activos prestados ya volvieron. Registra una salida con «Nuevo préstamo».'],
      vencidos: ['Ningún préstamo vencido', 'Todos los préstamos vigentes están dentro de su fecha pactada.'],
      devueltos: ['Aún no hay devoluciones', 'Cuando registres la devolución de un préstamo vigente aparecerá aquí.'],
      todos: ['No hay préstamos registrados', 'El primer préstamo que registres aparecerá en este libro.'],
    };
    const [titulo, texto] = textos[estado.filtro];
    const accion = estado.filtro === 'vigentes' || estado.filtro === 'todos'
      ? html`<button type="button" class="boton boton--sm" data-nuevo>Nuevo préstamo</button>` : '';
    return ui.vacio({ titulo, texto, accion });
  }

  function pintarRegistro() {
    if (!estado.cargado) return;
    const q = consulta();
    const delFiltro = estado.prestamos.filter(FILTROS[estado.filtro].pasa);
    const lista = delFiltro.filter((p) => coincide(p, q)).sort(orden);
    const visibles = paginas.cortar(lista);
    el.conteo.textContent = q
      ? `${plural(lista.length, 'coincidencia', 'coincidencias')} de ${delFiltro.length} ${FILTROS[estado.filtro].nombre}`
      : `${lista.length} de ${plural(estado.prestamos.length, 'préstamo registrado', 'préstamos registrados')}`;

    if (!lista.length) {
      el.registro.innerHTML = vacioRegistro(q);
    } else {
      el.registro.innerHTML = html`
        <div class="tabla-marco">
          <table class="tabla tabla--prestamos">
            <caption class="sr">Préstamos ${FILTROS[estado.filtro].nombre}${q ? ` que coinciden con ${estado.q.trim()}` : ''}</caption>
            ${CABEZA}
            <tbody>${visibles.map(fila)}</tbody>
          </table>
        </div>`;
    }
    ui.soloAdmin([...el.registro.querySelectorAll('[data-devolver], [data-nuevo]')]);
  }

  function refrescarLista() {
    sincronizarUrl();
    pintarSegmentos();
    pintarRegistro();
  }

  el.segmentos.addEventListener('click', (ev) => {
    const b = ev.target.closest('button[data-filtro]');
    if (!b || b.dataset.filtro === estado.filtro) return;
    estado.filtro = b.dataset.filtro;
    refrescarLista();
  });

  el.buscar.value = estado.q;
  el.buscar.addEventListener('input', ui.demorar(() => {
    estado.q = el.buscar.value;
    refrescarLista();
  }, 160));

  el.registro.addEventListener('click', (ev) => {
    const b = ev.target.closest('[data-devolver]');
    if (b) { abrirDevolucion(b.dataset.devolver); return; }
    if (ev.target.closest('[data-limpiar]')) {
      estado.q = '';
      el.buscar.value = '';
      refrescarLista();
      el.buscar.focus();
      return;
    }
    if (ev.target.closest('[data-ver-todos]')) { estado.filtro = 'todos'; refrescarLista(); return; }
    if (ev.target.closest('[data-reintentar]')) cargar({ primera: true });
  });

  document.addEventListener('click', (ev) => {
    if (ev.target.closest('[data-nuevo]')) abrirNuevo('');
  });

  // ── Diálogos: comunes ─────────────────────────────────────────────────────
  function limpiarCampo(input) {
    input.removeAttribute('aria-invalid');
    const sig = input.nextElementSibling;
    if (sig && sig.hasAttribute('data-error')) sig.remove();
  }

  /** Muestra el error de una mutación en el diálogo (o redirige si la sesión cayó). */
  function errorEnDialogo(e, form, hueco) {
    if (e?.code === 'UNAUTHENTICATED') { ui.error(e); return; }
    if (!(e instanceof ETER.ApiError)) console.error(e);
    if (e?.code === 'BAD_USER_INPUT') {
      ui.marcarErrores(form, e.errores);
      form.querySelector('[aria-invalid="true"]')?.focus();
    }
    hueco.innerHTML = ui.alerta(e);
  }

  const abrirDialogo = (dlg) => { if (!dlg.open) ui.abrir(dlg); };

  document.querySelectorAll('.dialogo [data-cerrar]').forEach((b) =>
    b.addEventListener('click', () => ui.cerrar(b.closest('dialog'))));

  // ── Nuevo préstamo ────────────────────────────────────────────────────────
  const campoN = nuevo.form.elements;

  function vigentePorPlaca(placa) {
    return estado.prestamos.find((p) => p.estado === 'VIGENTE' && p.placaInventario === placa) || null;
  }

  /** Activo conocido: de la lista ACTIVO del diálogo o, si no, del resumen que traen los préstamos. */
  function activoConocido(placa) {
    const a = estado.activos?.get(placa);
    if (a) return a;
    const p = estado.prestamos.find((x) => x.placaInventario === placa && x.activo);
    return p ? { placaInventario: placa, ...p.activo } : null;
  }

  function mostrarActivo() {
    const placa = campoN.placaInventario.value.trim();
    if (!placa) { nuevo.activo.innerHTML = ''; return; }
    const vigente = vigentePorPlaca(placa);
    const a = activoConocido(placa);
    if (vigente) {
      const pz = plazo(vigente);
      nuevo.activo.innerHTML = html`
        <div class="alerta alerta--aviso">
          <strong>${placa} ya está prestado</strong>
          <span>${a ? `${a.descripcion}. ` : ''}Lo tiene el custodio <span class="mono">${vigente.numDocumento}</span> desde el ${fmt.fechaHora(vigente.fechaSalida)}, con devolución pactada el ${fmt.fechaHora(vigente.fechaDevolucionPactada)} (${pz.texto}). Préstamos no acepta un segundo préstamo vigente del mismo activo.</span>
          <span class="fila"><button type="button" class="boton boton--sm" data-ir-devolver="${vigente.idPrestamo}">Registrar su devolución</button></span>
        </div>`;
      return;
    }
    if (a) {
      nuevo.activo.innerHTML = html`
        <div class="np-activo">
          ${ui.placa(placa, { sm: true, sede: a.codSede })}
          <div class="np-activo-texto">
            <strong>${a.descripcion}</strong>
            <span>Sede <span class="mono">${a.codSede}</span></span>
            ${a.estado && a.estado !== 'ACTIVO' ? html`<span>${ui.estadoActivo(a.estado)}</span>` : ''}
          </div>
        </div>`;
      return;
    }
    nuevo.activo.innerHTML = estado.activos && RX_PLACA.test(placa)
      ? html`<p class="ayuda">No aparece entre los activos disponibles. Al guardar, préstamos le pregunta al servicio de activos si la placa existe.</p>`
      : '';
  }

  async function cargarActivos() {
    const turno = ++estado.turnoActivos;
    nuevo.estadoLista.classList.remove('ayuda--error');
    nuevo.estadoLista.textContent = 'Letras, números y guiones, como AF-2026-0001. Consultando activos disponibles…';
    try {
      const activos = await api.activos({ estado: 'ACTIVO' });
      if (turno !== estado.turnoActivos) return;
      estado.activos = new Map((activos || []).map((a) => [a.placaInventario, a]));
      const libres = (activos || [])
        .filter((a) => !vigentePorPlaca(a.placaInventario))
        .sort((a, b) => a.placaInventario.localeCompare(b.placaInventario));
      nuevo.lista.innerHTML = html`${libres.map((a) => html`<option value="${a.placaInventario}">${a.descripcion} · ${a.codSede}</option>`)}`;
      nuevo.estadoLista.textContent = `Letras, números y guiones, como AF-2026-0001. La lista trae ${plural(libres.length, 'activo disponible', 'activos disponibles')}: en estado ACTIVO y sin préstamo vigente.`;
    } catch (e) {
      if (turno !== estado.turnoActivos) return;
      if (e?.code === 'UNAUTHENTICATED') { ui.error(e); return; }
      estado.activos = null;
      nuevo.lista.innerHTML = '';
      nuevo.estadoLista.classList.add('ayuda--error');
      nuevo.estadoLista.textContent = `No se pudo traer la lista de activos (${ui.mensajeError(e).detalle}). Escribe la placa: préstamos la valida con activos al guardar.`;
    }
    mostrarActivo();
  }

  function actualizarCorreo() {
    const v = campoN.numDocumento.value.trim();
    nuevo.correo.innerHTML = RX_DOC.test(v)
      ? html`5 a 20 letras o números. El aviso le llega a <span class="mono">${v}@eter.edu.co</span>.`
      : html`5 a 20 letras o números, sin espacios, puntos ni guiones. El aviso le llega a <span class="mono">documento@eter.edu.co</span>.`;
  }

  function actualizarPlazo() {
    const s = ms(ui.aLocalDateTime(campoN.fechaSalida.value));
    const e = ms(ui.aLocalDateTime(campoN.fechaDevolucionPactada.value));
    nuevo.plazo.classList.remove('ayuda--error');
    if (Number.isNaN(s) || Number.isNaN(e)) { nuevo.plazo.textContent = 'Debe quedar después de la salida.'; return; }
    if (e <= s) {
      nuevo.plazo.textContent = 'Debe quedar después de la salida.';
      nuevo.plazo.classList.add('ayuda--error');
      return;
    }
    const horas = Math.round((e - s) / 3600000);
    const d = Math.floor(horas / 24), h = horas % 24;
    const partes = [d ? plural(d, 'día', 'días') : '', h ? plural(h, 'hora', 'horas') : ''].filter(Boolean);
    nuevo.plazo.textContent = `Plazo de ${partes.join(' y ') || 'menos de una hora'}.`;
  }

  function abrirNuevo(placa = '') {
    if (!sesion.esAdmin()) { ui.toast(ui.SIN_PERMISO, { tipo: 'aviso' }); return; }
    nuevo.form.reset();
    ui.limpiarErrores(nuevo.form);
    nuevo.alerta.innerHTML = '';
    campoN.placaInventario.value = placa;
    campoN.fechaSalida.value = ui.fechaInput(0);
    campoN.fechaDevolucionPactada.value = ui.fechaInput(7, 18, 0);
    const docs = [...new Set(estado.prestamos.map((p) => p.numDocumento))].sort();
    nuevo.custodios.innerHTML = html`${docs.map((d) => html`<option value="${d}"></option>`)}`;
    actualizarCorreo();
    actualizarPlazo();
    mostrarActivo();
    abrirDialogo(nuevo.dlg);
    if (placa) campoN.numDocumento.focus();
    cargarActivos();
  }

  function validarNuevo(i) {
    const e = [];
    if (!i.placaInventario) e.push({ campo: 'placaInventario', mensaje: 'es obligatorio' });
    else if (!RX_PLACA.test(i.placaInventario)) e.push({ campo: 'placaInventario', mensaje: 'solo admite letras, números y guiones (ej. AF-2026-0001)' });
    if (!RX_DOC.test(i.numDocumento)) e.push({ campo: 'numDocumento', mensaje: 'debe tener entre 5 y 20 letras o números, sin espacios ni símbolos' });
    if (!i.fechaSalida) e.push({ campo: 'fechaSalida', mensaje: 'es obligatorio' });
    if (!i.fechaDevolucionPactada) e.push({ campo: 'fechaDevolucionPactada', mensaje: 'es obligatorio' });
    else if (i.fechaSalida && ms(i.fechaDevolucionPactada) <= ms(i.fechaSalida)) {
      e.push({ campo: 'fechaDevolucionPactada', mensaje: 'debe ser posterior a la fecha de salida' });
    }
    return e;
  }

  nuevo.form.addEventListener('input', (ev) => {
    const t = ev.target;
    if (!t.name) return;
    limpiarCampo(t);
    if (t.name === 'placaInventario') mostrarActivo();
    if (t.name === 'numDocumento') actualizarCorreo();
    if (t.name === 'fechaSalida' || t.name === 'fechaDevolucionPactada') actualizarPlazo();
  });

  nuevo.activo.addEventListener('click', (ev) => {
    const b = ev.target.closest('[data-ir-devolver]');
    if (!b) return;
    ui.cerrar(nuevo.dlg);
    abrirDevolucion(b.dataset.irDevolver);
  });

  nuevo.form.addEventListener('submit', async (ev) => {
    ev.preventDefault();
    const btn = nuevo.form.querySelector('[type="submit"]');
    ui.limpiarErrores(nuevo.form);
    nuevo.alerta.innerHTML = '';
    const d = ui.datosForm(nuevo.form);
    const input = {
      placaInventario: d.placaInventario || '',
      numDocumento: d.numDocumento || '',
      fechaSalida: ui.aLocalDateTime(d.fechaSalida) || '',
      fechaDevolucionPactada: ui.aLocalDateTime(d.fechaDevolucionPactada) || '',
    };
    ui.ocupado(btn, true);
    try {
      const errores = validarNuevo(input);
      if (errores.length) throw new ETER.ApiError('BAD_USER_INPUT', 'Solicitud inválida.', { errores });
      const creado = await api.crearPrestamo(input);
      ui.cerrar(nuevo.dlg);
      ui.toast(`Préstamo registrado: ${creado.placaInventario} a ${creado.numDocumento} hasta el ${fmt.fechaHora(creado.fechaDevolucionPactada)}. La notificación al custodio y el movimiento del kardex llegan en unos segundos vía Kafka.`, { ms: 8000 });
      const p = { ...creado, vencido: esVencido(creado) };
      if (!FILTROS[estado.filtro].pasa(p)) estado.filtro = 'vigentes';
      if (!coincide(p, consulta())) { estado.q = ''; el.buscar.value = ''; }
      estado.reciente = creado.idPrestamo;
      sincronizarUrl();
      await cargar();
      setTimeout(() => { estado.reciente = null; }, 3000);
      setTimeout(() => ui.actualizarContador(), 3500);
    } catch (e) {
      errorEnDialogo(e, nuevo.form, nuevo.alerta);
    } finally {
      ui.ocupado(btn, false);
    }
  });

  nuevo.dlg.addEventListener('close', () => { estado.turnoActivos++; });

  // ── Registrar devolución ──────────────────────────────────────────────────
  const campoD = dev.form.elements;

  function actualizarCumple() {
    const p = estado.enDevolucion;
    const v = campoD.fechaDevolucionReal.value;
    dev.cumple.classList.remove('ayuda--aviso');
    if (!p || !v) { dev.cumple.textContent = ''; return; }
    const c = cumplimiento(ui.aLocalDateTime(v), p.fechaDevolucionPactada);
    if (!c) { dev.cumple.textContent = ''; return; }
    if (c.tono === 'tarde') {
      dev.cumple.textContent = `Queda como devolución tardía: ${c.texto} frente a lo pactado (${fmt.fechaHora(p.fechaDevolucionPactada)}).`;
      dev.cumple.classList.add('ayuda--aviso');
    } else {
      dev.cumple.textContent = 'Queda dentro del plazo pactado.';
    }
  }

  function abrirDevolucion(id) {
    if (!sesion.esAdmin()) { ui.toast(ui.SIN_PERMISO, { tipo: 'aviso' }); return; }
    const p = estado.prestamos.find((x) => x.idPrestamo === id);
    if (!p) {
      ui.toast(estado.cargado ? `No hay un préstamo con id ${id}.` : 'No se pudo cargar el préstamo a devolver.', { tipo: 'error' });
      return;
    }
    if (p.estado !== 'VIGENTE') {
      ui.toast(`El préstamo de ${p.placaInventario} ya está ${p.estado === 'DEVUELTO' ? 'devuelto' : 'cancelado'}; no hay devolución que registrar.`, { tipo: 'aviso' });
      return;
    }
    estado.enDevolucion = p;
    dev.form.reset();
    ui.limpiarErrores(dev.form);
    dev.alerta.innerHTML = '';
    dev.form.dataset.id = p.idPrestamo;
    dev.sello.innerHTML = ui.selloPrestamo(p);
    const pz = plazo(p);
    dev.resumen.innerHTML = html`
      <dl class="ficha ficha--prestamo">
        <div><dt>Placa</dt><dd>${ui.codigo(p.placaInventario)}</dd></div>
        <div><dt>Custodio</dt><dd><span class="mono">${p.numDocumento}</span></dd></div>
        <div class="ficha-ancha"><dt>Activo</dt><dd>${p.activo
          ? html`${p.activo.descripcion} <span class="tenue">· Sede ${p.activo.codSede}</span>`
          : html`<span class="tenue">Sin datos del activo</span>`}</dd></div>
        <div><dt>Salida</dt><dd><span class="mono">${fmt.registro(p.fechaSalida)}</span></dd></div>
        <div><dt>Devolución pactada</dt><dd><span class="mono">${fmt.registro(p.fechaDevolucionPactada)}</span><span class="plazo${pz.tono ? ` plazo--${pz.tono}` : ''}">${pz.texto}</span></dd></div>
      </dl>`;
    const ahora = ui.fechaInput(0);
    const salida = ui.aInputLocal(p.fechaSalida);
    campoD.fechaDevolucionReal.max = ahora;
    if (salida && salida <= ahora) campoD.fechaDevolucionReal.min = salida;
    else campoD.fechaDevolucionReal.removeAttribute('min');
    campoD.fechaDevolucionReal.value = ahora;
    actualizarCumple();
    abrirDialogo(dev.dlg);
  }

  dev.form.addEventListener('input', (ev) => {
    if (!ev.target.name) return;
    limpiarCampo(ev.target);
    actualizarCumple();
  });

  dev.form.addEventListener('submit', async (ev) => {
    ev.preventDefault();
    const p = estado.enDevolucion;
    if (!p) return;
    const btn = dev.form.querySelector('[type="submit"]');
    ui.limpiarErrores(dev.form);
    dev.alerta.innerHTML = '';
    const fecha = ui.aLocalDateTime(campoD.fechaDevolucionReal.value.trim());
    ui.ocupado(btn, true);
    try {
      const errores = [];
      if (!fecha) errores.push({ campo: 'fechaDevolucionReal', mensaje: 'es obligatorio' });
      else if (ms(fecha) > Date.now()) errores.push({ campo: 'fechaDevolucionReal', mensaje: 'debe ser una fecha pasada o presente' });
      else if (ms(fecha) < ms(p.fechaSalida)) errores.push({ campo: 'fechaDevolucionReal', mensaje: `no puede ser anterior a la salida (${fmt.registro(p.fechaSalida)})` });
      if (errores.length) throw new ETER.ApiError('BAD_USER_INPUT', 'Solicitud inválida.', { errores });
      const r = await api.registrarDevolucion(p.idPrestamo, fecha);
      ui.cerrar(dev.dlg);
      const c = cumplimiento(r.fechaDevolucionReal, r.fechaDevolucionPactada);
      ui.toast(`Devolución registrada: ${r.placaInventario}${c?.tono === 'tarde' ? `, ${c.texto}` : ', a tiempo'}. El movimiento del kardex y la notificación al custodio llegan en unos segundos vía Kafka.`, { ms: 8000 });
      estado.reciente = r.idPrestamo;
      await cargar();
      setTimeout(() => { estado.reciente = null; }, 3000);
      setTimeout(() => ui.actualizarContador(), 3500);
    } catch (e) {
      errorEnDialogo(e, dev.form, dev.alerta);
    } finally {
      ui.ocupado(btn, false);
    }
  });

  dev.dlg.addEventListener('close', () => { estado.enDevolucion = null; });

  // ── Arranque ──────────────────────────────────────────────────────────────
  ui.soloAdmin(el.nuevo);
  el.nuevo.addEventListener('click', () => abrirNuevo(''));

  (async () => {
    await cargar({ primera: true });
    const pDevolver = ui.param('devolver');
    const pNuevo = ui.param('nuevo');
    if (pDevolver) abrirDevolucion(pDevolver.trim());
    else if (pNuevo !== null) abrirNuevo(pNuevo.trim());
    sincronizarUrl(['nuevo', 'devolver']);
  })();
})();
