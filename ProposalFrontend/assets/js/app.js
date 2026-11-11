/*
 * ETER · armazon de la aplicacion. Expone window.ETER.ui.
 *
 * Cada pagina, al empezar:   if (!ETER.ui.montar({ pagina: 'activos' })) return;
 * montar() revisa la sesion, dibuja el menu lateral y la barra superior (ruta,
 * tema, campana y menu de usuario), pone el icono del modulo en .cabecera y
 * actualiza el contador de notificaciones.
 *
 * Para HTML dinamico use ui.html`...`: escapa todo lo interpolado salvo lo que
 * ya sea ui.html / ui.crudo / un arreglo de ellos. Nunca concatene datos del
 * servidor en innerHTML sin pasar por ui.html o ui.esc.
 */
(function () {
  'use strict';
  const ETER = window.ETER;
  const { sesion, api } = ETER;

  // ── HTML seguro ───────────────────────────────────────────────────────────
  const esc = (v) => String(v ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]);
  class Crudo { constructor(h) { this.h = h; } toString() { return this.h; } }
  const crudo = (h) => new Crudo(String(h ?? ''));
  const valor = (v) => (v instanceof Crudo ? v.h : Array.isArray(v) ? v.map(valor).join('') : v === false || v == null ? '' : esc(v));
  function html(partes, ...valores) {
    let out = partes[0];
    valores.forEach((v, i) => { out += valor(v) + partes[i + 1]; });
    return new Crudo(out);
  }

  // ── Iconos (trazo 1.75, 24×24) ────────────────────────────────────────────
  const ICONOS = {
    panel: '<rect x="3.5" y="3.5" width="7" height="8" rx="1.5"/><rect x="13.5" y="3.5" width="7" height="5" rx="1.5"/><rect x="13.5" y="11.5" width="7" height="9" rx="1.5"/><rect x="3.5" y="14.5" width="7" height="6" rx="1.5"/>',
    activos: '<path d="M20.5 13.3 13.3 20.5a1.9 1.9 0 0 1-2.7 0L3.5 13.4V3.5h9.9l7.1 7.1a1.9 1.9 0 0 1 0 2.7z"/><circle cx="8" cy="8" r="1.4"/>',
    prestamos: '<path d="M4 8h13l-3.5-3.5M20 16H7l3.5 3.5"/>',
    inventario: '<path d="M5 3.5h10.5L19 7v13.5H5z"/><path d="M9 9.5h6M9 13h6M9 16.5h3.5"/>',
    notificaciones: '<path d="M6 16.5v-5a6 6 0 1 1 12 0v5l1.5 2h-15z"/><path d="M10 20.5a2 2 0 0 0 4 0"/>',
    casa: '<path d="m3.5 11 8.5-7 8.5 7"/><path d="M6 9.5v11h12v-11"/><path d="M10 20.5v-6h4v6"/>',
    caja: '<path d="M12 3.5 20 8v8l-8 4.5L4 16V8z"/><path d="m4 8 8 4.5L20 8M12 12.5v8"/>',
    movimientos: '<path d="M7.5 20V4M7.5 4 4 7.5M7.5 4 11 7.5M16.5 4v16M16.5 20 13 16.5M16.5 20l3.5-3.5"/>',
    asignaciones: '<path d="M9 4.5H6v16h12v-16h-3"/><rect x="9" y="3" width="6" height="3.5" rx="1"/><path d="m9 13.5 2 2 4-4"/>',
    megafono: '<path d="M4 10v4l3.5.5L17 19V5L7.5 9.5z"/><path d="m8 15 1.3 5h2.4l-1.2-4.2M20 10v4"/>',
    universidad: '<path d="M12 4.5 2.5 9.5l9.5 5 9.5-5z"/><path d="M6.5 12v4.5c0 1.1 2.5 2.5 5.5 2.5s5.5-1.4 5.5-2.5V12"/>',
    menu: '<path d="M4 6.5h16M4 12h16M4 17.5h16"/>',
    menuCerrar: '<path d="M4 6.5h16M10 12h10M4 17.5h16"/><path d="M6.5 9.5 4 12l2.5 2.5"/>',
    sol: '<circle cx="12" cy="12" r="4"/><path d="M12 3v2M12 19v2M3 12h2M19 12h2M5.6 5.6 7 7M17 17l1.4 1.4M18.4 5.6 17 7M7 17l-1.4 1.4"/>',
    luna: '<path d="M20 14.2A8 8 0 1 1 9.8 4a6.4 6.4 0 0 0 10.2 10.2z"/>',
    abajo: '<path d="m6 9.5 6 6 6-6"/>',
    reiniciar: '<path d="M4 12a8 8 0 1 0 2.6-5.9L4 8.5"/><path d="M4 4v4.5h4.5"/>',
    enlace: '<path d="M10 14a4 4 0 0 0 5.7 0l3-3a4 4 0 0 0-5.7-5.7l-1 1"/><path d="M14 10a4 4 0 0 0-5.7 0l-3 3a4 4 0 0 0 5.7 5.7l1-1"/>',
    mas: '<path d="M12 5v14M5 12h14"/>',
    buscar: '<circle cx="11" cy="11" r="6.5"/><path d="m20 20-4.2-4.2"/>',
    salir: '<path d="M14.5 4H19v16h-4.5M10 8l-4 4 4 4M6 12h10"/>',
    cerrar: '<path d="M6 6l12 12M18 6 6 18"/>',
    flecha: '<path d="M5 12h14M13 6l6 6-6 6"/>',
    volver: '<path d="M19 12H5M11 6l-6 6 6 6"/>',
    reloj: '<circle cx="12" cy="12" r="8.5"/><path d="M12 7.5V12l3 2"/>',
    check: '<path d="m5 12.5 4.5 4.5L19 7.5"/>',
    alerta: '<path d="M12 4 2.8 19.5h18.4z"/><path d="M12 10v4.5M12 17.2v.1"/>',
    editar: '<path d="M4 20h4L19 9l-4-4L4 16z"/><path d="m13.5 6.5 4 4"/>',
    baja: '<path d="M5 7h14M10 7V4.5h4V7M7 7l1 13h8l1-13"/>',
    filtro: '<path d="M4 5h16l-6 7.5V19l-4-2v-4.5z"/>',
    usuario: '<circle cx="12" cy="8.5" r="3.5"/><path d="M5 20a7 7 0 0 1 14 0"/>',
    senal: '<path d="M5.6 15.5a7 7 0 0 1 12.8 0M8.7 15.5a3.5 3.5 0 0 1 6.6 0"/><circle cx="12" cy="17.5" r="1"/>',
    devolver: '<path d="M9 14 4 9l5-5"/><path d="M4 9h10.5a5.5 5.5 0 0 1 0 11H11"/>',
    llave: '<circle cx="8" cy="15" r="4"/><path d="m11 12 8.5-8.5M16 7l2.5 2.5M14 9l2 2"/>',
    servidor: '<path d="M4 4.5h16v6H4zM4 13.5h16v6H4z"/><path d="M7.5 7.5h.01M7.5 16.5h.01"/>',
  };
  const icono = (nombre, clase = '') =>
    crudo(`<svg class="icono ${clase}" viewBox="0 0 24 24" aria-hidden="true" focusable="false">${ICONOS[nombre] || ''}</svg>`);
  /** Logo hexagonal de ETER. */
  const logo = () => crudo(`<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" aria-hidden="true" focusable="false">
    <polygon points="12,1.5 22,7 22,17 12,22.5 2,17 2,7" stroke-width="1.5"/>
    <circle cx="12" cy="12" r="2.5" fill="currentColor" stroke="none"/>
    <path stroke-width="1.2" d="M12 9.5V6M14.2 10.5l3.3-2M14.2 13.5l3.3 2M12 14.5V18M9.8 13.5l-3.3 2M9.8 10.5l-3.3-2"/>
  </svg>`);

  // ── Formato ───────────────────────────────────────────────────────────────
  const aFecha = (iso) => (iso ? new Date(iso) : null);
  const valida = (d) => d && !Number.isNaN(d.getTime());
  const MESES = ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'sep', 'oct', 'nov', 'dic'];
  const dos = (n) => String(n).padStart(2, '0');
  const moneda = new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP', maximumFractionDigits: 0 });
  const numero = new Intl.NumberFormat('es-CO');
  const fmt = {
    /** "28 oct 2026" */
    fecha(iso) { const d = aFecha(iso); return valida(d) ? `${d.getDate()} ${MESES[d.getMonth()]} ${d.getFullYear()}` : '—'; },
    /** "28 oct 2026, 08:00" */
    fechaHora(iso) { const d = aFecha(iso); return valida(d) ? `${fmt.fecha(iso)}, ${dos(d.getHours())}:${dos(d.getMinutes())}` : '—'; },
    /** "2026-10-28 08:00": para columnas de tablas y kardex (con .mono). */
    registro(iso) { const d = aFecha(iso); return valida(d) ? `${d.getFullYear()}-${dos(d.getMonth() + 1)}-${dos(d.getDate())} ${dos(d.getHours())}:${dos(d.getMinutes())}` : '—'; },
    /** "hace 5 min", "ayer", "en 3 días" */
    relativa(iso) {
      const d = aFecha(iso);
      if (!valida(d)) return '—';
      const s = Math.round((d.getTime() - Date.now()) / 1000);
      const abs = Math.abs(s);
      const rtf = new Intl.RelativeTimeFormat('es', { numeric: 'auto' });
      if (abs < 60) return 'ahora';
      if (abs < 3600) return rtf.format(Math.round(s / 60), 'minute');
      if (abs < 86400) return rtf.format(Math.round(s / 3600), 'hour');
      if (abs < 86400 * 30) return rtf.format(Math.round(s / 86400), 'day');
      return fmt.fecha(iso);
    },
    /** Días enteros desde hoy hasta la fecha (negativo si ya pasó). */
    diasHasta(iso) {
      const d = aFecha(iso);
      if (!valida(d)) return null;
      const hoy = new Date(); hoy.setHours(0, 0, 0, 0);
      const dia = new Date(d); dia.setHours(0, 0, 0, 0);
      return Math.round((dia - hoy) / 86400000);
    },
    moneda: (n) => (n == null ? '—' : moneda.format(Number(n))),
    numero: (n) => (n == null ? '—' : numero.format(Number(n))),
  };

  // ── Vocabulario del dominio ───────────────────────────────────────────────
  const ESTADO_ACTIVO = { ACTIVO: ['Activo', 'ok'], MANTENIMIENTO: ['En mantenimiento', 'aviso'], BAJA: ['De baja', 'neutro'] };
  const ESTADO_PRESTAMO = { VIGENTE: ['Vigente', 'info'], DEVUELTO: ['Devuelto', 'ok'], CANCELADO: ['Cancelado', 'neutro'] };
  const TIPO_MOVIMIENTO = {
    ALTA: 'Alta', BAJA: 'Baja', TRASLADO: 'Traslado', MANTENIMIENTO: 'Mantenimiento',
    ASIGNACION: 'Asignación', PRESTAMO: 'Préstamo', DEVOLUCION: 'Devolución',
  };
  const TIPO_NOTIFICACION = { PRESTAMO_CREADO: 'Préstamo registrado', PRESTAMO_DEVUELTO: 'Préstamo devuelto', PRESTAMO_VENCIDO: 'Préstamo vencido' };

  const estadoActivo = (e) => {
    const [txt, tono] = ESTADO_ACTIVO[e] || [e, 'neutro'];
    return html`<span class="estado estado--${tono}">${txt}</span>`;
  };
  /** Un préstamo VIGENTE con vencido=true se muestra como Vencido. */
  const estadoPrestamo = (p) => {
    if (p.estado === 'VIGENTE' && p.vencido) return html`<span class="estado estado--peligro">Vencido</span>`;
    const [txt, tono] = ESTADO_PRESTAMO[p.estado] || [p.estado, 'neutro'];
    return html`<span class="estado estado--${tono}">${txt}</span>`;
  };
  /** Estado destacado: úselo en detalles, no en filas de tabla. */
  const selloPrestamo = (p) => {
    if (p.estado === 'VIGENTE' && p.vencido) return html`<span class="sello">Vencido</span>`;
    if (p.estado === 'VIGENTE') return html`<span class="sello sello--info">En préstamo</span>`;
    if (p.estado === 'DEVUELTO') return html`<span class="sello sello--ok">Devuelto</span>`;
    return html`<span class="sello sello--neutro">${p.estado}</span>`;
  };

  // Código de barras decorativo derivado del texto (cada placa tiene el suyo).
  function barras(texto) {
    const seq = [1, 1, 1, 1];
    [...String(texto)].forEach((ch, i) => {
      const c = ch.charCodeAt(0);
      seq.push(1 + (c % 3), 1 + ((c >> 2) % 2), 1 + ((c >> 3) % 3), 1 + ((c + i) % 2));
    });
    seq.push(1, 1, 1, 1);
    const total = seq.reduce((a, b) => a + b, 0);
    let x = 0;
    const tramos = seq.map((w, i) => {
      const a = ((x / total) * 100).toFixed(2), b = (((x + w) / total) * 100).toFixed(2);
      x += w;
      return `${i % 2 ? 'transparent' : '#141716'} ${a}% ${b}%`;
    });
    return `linear-gradient(90deg, ${tramos.join(', ')})`;
  }

  /** Etiqueta de inventario. opciones: { sm, sede } */
  const placa = (codigo, { sm = false, sede = '' } = {}) => html`
    <div class="placa${sm ? ' placa--sm' : ''}" role="img" aria-label="Placa de inventario ${codigo}">
      <span class="placa-titular">Patrimonio · ETER</span>
      <span class="placa-codigo">${codigo}</span>
      <span class="placa-barras" style="background-image: ${crudo(barras(codigo))}"></span>
      <span class="placa-pie"><span>NIT ${sesion.usuario()?.nitUniv || ''}</span><span>${sede}</span></span>
    </div>`;

  const codigo = (texto, href) => (href ? html`<a class="codigo" href="${href}">${texto}</a>` : html`<span class="codigo">${texto}</span>`);
  const urlActivo = (p) => `activo.html?placa=${encodeURIComponent(p)}`;
  const enlaceActivo = (p) => codigo(p, urlActivo(p));

  // ── Errores ───────────────────────────────────────────────────────────────
  function mensajeError(err) {
    const m = err?.message || 'Error desconocido.';
    switch (err?.code) {
      case 'UNAUTHENTICATED': return { titulo: 'Tu sesión no es válida', detalle: 'El token falta, venció o no lo firmó este sistema. Vuelve a ingresar.' };
      case 'FORBIDDEN': return { titulo: 'Sin permiso', detalle: m };
      case 'NOT_FOUND': return { titulo: 'No encontrado', detalle: m };
      case 'CONFLICT': return { titulo: 'No se pudo completar', detalle: m };
      case 'BAD_USER_INPUT': return { titulo: 'Revisa los datos', detalle: err.errores?.length ? 'Hay campos con problemas.' : m };
      case 'SERVICE_UNAVAILABLE': return { titulo: 'Servicio no disponible', detalle: m };
      case 'SIN_CONEXION': return { titulo: 'Sin conexión con el gateway', detalle: m };
      default: return { titulo: 'Algo falló', detalle: m };
    }
  }
  /** Caja .alerta con el error (incluye la lista de campos si la hay). */
  function alerta(err) {
    const { titulo, detalle } = mensajeError(err);
    return html`<div class="alerta" role="alert"><strong>${titulo}</strong><span>${detalle}</span>${
      err?.errores?.length ? html`<ul>${err.errores.map((e) => html`<li><span class="mono">${e.campo}</span>: ${e.mensaje}</li>`)}</ul>` : ''
    }</div>`;
  }
  /**
   * Maneja un error de la API. UNAUTHENTICATED cierra la sesión y lleva a
   * ingresar.html. Con { en: elemento } pinta la alerta ahí; si no, toast.
   */
  function error(err, { en } = {}) {
    if (!(err instanceof ETER.ApiError)) console.error(err);
    if (err?.code === 'UNAUTHENTICATED' && !sesion.demo) {
      sesion.cerrar();
      location.replace('ingresar.html?motivo=sesion');
      return;
    }
    if (en) { en.innerHTML = alerta(err); return; }
    const { titulo, detalle } = mensajeError(err);
    toast(`${titulo}. ${detalle}`, { tipo: 'error' });
  }
  /** Marca en el formulario los campos de err.errores ([{campo, mensaje}]). Devuelve cuántos encontró. */
  function marcarErrores(form, errores = []) {
    limpiarErrores(form);
    let n = 0;
    for (const e of errores) {
      const campo = form.querySelector(`[name="${CSS.escape(e.campo)}"]`);
      if (!campo) continue;
      n++;
      campo.setAttribute('aria-invalid', 'true');
      const p = document.createElement('span');
      p.className = 'campo-error';
      p.dataset.error = '';
      p.textContent = e.mensaje;
      campo.insertAdjacentElement('afterend', p);
    }
    return n;
  }
  function limpiarErrores(form) {
    form.querySelectorAll('[data-error]').forEach((x) => x.remove());
    form.querySelectorAll('[aria-invalid]').forEach((x) => x.removeAttribute('aria-invalid'));
  }

  // ── Avisos flotantes ──────────────────────────────────────────────────────
  function toast(texto, { tipo = 'info', ms = 4500 } = {}) {
    let caja = document.getElementById('toasts');
    if (!caja) {
      caja = document.createElement('div');
      caja.id = 'toasts';
      caja.className = 'toasts';
      caja.setAttribute('aria-live', 'polite');
      document.body.append(caja);
    }
    const t = document.createElement('div');
    t.className = `toast${tipo !== 'info' ? ` toast--${tipo}` : ''}`;
    t.innerHTML = html`${icono(tipo === 'error' ? 'alerta' : tipo === 'aviso' ? 'reloj' : 'check')}<span>${texto}</span>`;
    caja.append(t);
    setTimeout(() => t.remove(), ms);
  }

  // ── Diálogos ──────────────────────────────────────────────────────────────
  const abrir = (dlg) => { dlg.showModal(); dlg.querySelector('[autofocus], input, select, textarea')?.focus(); };
  const cerrar = (dlg) => dlg.close();
  /** Confirmación propia (el navegador no muestra confirm()). Devuelve Promise<boolean>. */
  function confirmar({ titulo, texto, accion = 'Confirmar', peligro = false }) {
    return new Promise((resolver) => {
      const dlg = document.createElement('dialog');
      dlg.className = 'dialogo';
      dlg.innerHTML = html`
        <div class="dialogo-cabeza"><h2 class="dialogo-titulo">${titulo}</h2></div>
        <div class="dialogo-cuerpo"><p>${texto}</p></div>
        <div class="dialogo-pie">
          <button type="button" class="boton" data-no>Cancelar</button>
          <button type="button" class="boton ${peligro ? 'boton--peligro' : 'boton--primario'}" data-si>${accion}</button>
        </div>`;
      document.body.append(dlg);
      const fin = (v) => { dlg.close(); dlg.remove(); resolver(v); };
      dlg.querySelector('[data-no]').onclick = () => fin(false);
      dlg.querySelector('[data-si]').onclick = () => fin(true);
      dlg.addEventListener('cancel', (ev) => { ev.preventDefault(); fin(false); });
      dlg.showModal();
      dlg.querySelector('[data-si]').focus();
    });
  }

  // ── Formularios ───────────────────────────────────────────────────────────
  /** FormData -> objeto con strings recortados; los vacíos quedan fuera. */
  function datosForm(form) {
    const o = {};
    for (const [k, v] of new FormData(form)) {
      const t = typeof v === 'string' ? v.trim() : v;
      if (t !== '') o[k] = t;
    }
    return o;
  }
  /** Valor de <input type="datetime-local"> -> "2026-10-28T08:00:00". */
  const aLocalDateTime = (v) => (!v ? v : v.length === 16 ? `${v}:00` : v);
  /** "2026-10-28T08:00:00" -> valor para <input type="datetime-local">. */
  const aInputLocal = (iso) => (iso ? String(iso).slice(0, 16) : '');
  /** Fecha para un input datetime-local: hoy + dias, a la hora indicada (o la actual). */
  function fechaInput(dias = 0, hora, minuto = 0) {
    const d = new Date();
    d.setDate(d.getDate() + dias);
    if (hora != null) d.setHours(hora, minuto, 0, 0);
    return ETER.isoLocal(d).slice(0, 16);
  }
  const ocupado = (boton, si = true) => {
    boton.toggleAttribute('disabled', si);
    boton.setAttribute('aria-busy', String(si));
  };

  // ── Estados de carga y vacío ──────────────────────────────────────────────
  /** Filas de esqueleto para el <tbody> de una tabla de n columnas. */
  const cargandoFilas = (columnas, filas = 5) =>
    crudo(Array.from({ length: filas }, (_, i) =>
      `<tr aria-hidden="true">${Array.from({ length: columnas }, (_, j) =>
        `<td><span class="esqueleto" style="width:${40 + ((i * 7 + j * 13) % 50)}%"></span></td>`).join('')}</tr>`).join(''));
  const cargandoBloque = (lineas = 3) =>
    crudo(`<div class="pila" aria-hidden="true">${Array.from({ length: lineas }, (_, i) =>
      `<span class="esqueleto" style="width:${92 - i * 17}%"></span>`).join('')}</div>`);
  /** Estado vacío. accion: ui.html del botón o enlace, opcional. */
  const vacio = ({ titulo, texto, accion }) =>
    html`<div class="vacio"><p class="vacio-titulo">${titulo}</p><p>${texto}</p>${accion || ''}</div>`;

  // ── Varios ────────────────────────────────────────────────────────────────
  const param = (nombre) => new URLSearchParams(location.search).get(nombre);
  const demorar = (fn, ms = 250) => { let t; return (...a) => { clearTimeout(t); t = setTimeout(() => fn(...a), ms); }; };
  const SIN_PERMISO = 'Tu rol no permite esta operación. Necesitas el rol ADMIN.';
  /** Deshabilita los botones (o enlaces .boton) de escritura si el usuario no es ADMIN. */
  function soloAdmin(...botones) {
    if (sesion.esAdmin()) return;
    botones.flat().filter(Boolean).forEach((b) => {
      b.title = SIN_PERMISO;
      if (b.tagName === 'A') {
        b.removeAttribute('href');
        b.setAttribute('aria-disabled', 'true');
      } else {
        b.disabled = true;
      }
    });
  }

  // ── Paginación en el cliente ──────────────────────────────────────────────
  /**
   * Controles de paginación dentro de `caja`.
   *   const pag = ui.paginador(caja, { alCambiar: pintar });
   *   const visibles = pag.cortar(lista);   // recorta y dibuja los controles
   * Si cambia el largo de la lista (un filtro, por ejemplo) vuelve a la página 1.
   * alCambiar se llama al pasar de página o cambiar el tamaño.
   */
  function paginador(caja, { tamano = 10, tamanos = [10, 25, 50], alCambiar }) {
    let pagina = 1;
    let porPagina = tamano;
    let total = 0;

    /** Números a mostrar: primera, última y vecinas de la actual; null = puntos. */
    function numeros(paginas) {
      const visibles = [...new Set([1, paginas, pagina - 1, pagina, pagina + 1])]
        .filter((n) => n >= 1 && n <= paginas).sort((a, b) => a - b);
      return visibles.flatMap((n, i) => (i && n - visibles[i - 1] > 1 ? [null, n] : [n]));
    }

    function dibujar() {
      const paginas = Math.max(1, Math.ceil(total / porPagina));
      caja.hidden = total <= tamanos[0];
      if (caja.hidden) { caja.innerHTML = ''; return; }
      const desde = (pagina - 1) * porPagina + 1;
      const hasta = Math.min(total, pagina * porPagina);
      caja.innerHTML = html`
        <div class="paginador">
          <label class="paginador-tamano">Filas por página
            <select class="selector" data-tamano>
              ${tamanos.map((t) => html`<option value="${t}"${crudo(t === porPagina ? ' selected' : '')}>${t}</option>`)}
            </select>
          </label>
          <span>${fmt.numero(desde)}–${fmt.numero(hasta)} de ${fmt.numero(total)}</span>
          <nav class="paginador-nav" aria-label="Paginación">
            <button type="button" class="pagina-boton" data-pagina="${pagina - 1}" aria-label="Página anterior"${crudo(pagina === 1 ? ' disabled' : '')}>${icono('volver')}</button>
            ${numeros(paginas).map((n) => (n === null
              ? html`<span class="paginador-puntos" aria-hidden="true">…</span>`
              : html`<button type="button" class="pagina-boton" data-pagina="${n}" aria-label="Página ${n}"${crudo(n === pagina ? ' aria-current="page"' : '')}>${n}</button>`))}
            <button type="button" class="pagina-boton" data-pagina="${pagina + 1}" aria-label="Página siguiente"${crudo(pagina === paginas ? ' disabled' : '')}>${icono('flecha')}</button>
          </nav>
        </div>`;
    }

    caja.addEventListener('click', (ev) => {
      const b = ev.target.closest('[data-pagina]');
      if (!b || b.disabled) return;
      pagina = Number(b.dataset.pagina);
      alCambiar();
    });
    caja.addEventListener('change', (ev) => {
      if (!ev.target.matches('[data-tamano]')) return;
      porPagina = Number(ev.target.value);
      pagina = 1;
      alCambiar();
    });

    return {
      cortar(lista) {
        if (lista.length !== total) pagina = 1;
        total = lista.length;
        pagina = Math.min(pagina, Math.max(1, Math.ceil(total / porPagina)));
        dibujar();
        return lista.slice((pagina - 1) * porPagina, pagina * porPagina);
      },
    };
  }

  // ── Armazón ───────────────────────────────────────────────────────────────
  const MENU = [
    { id: 'inicio', titulo: 'Inicio', icono: 'casa', items: [['panel', 'index.html', 'Dashboard', 'panel']] },
    {
      id: 'gestion', titulo: 'Gestión de activos', icono: 'caja',
      items: [['activos', 'activos.html', 'Activos fijos', 'activos'], ['prestamos', 'prestamos.html', 'Préstamos temporales', 'prestamos']],
    },
    {
      id: 'inventario', titulo: 'Inventario', icono: 'inventario',
      items: [['inventario', 'inventario.html', 'Movimientos', 'movimientos'], ['asignaciones', 'inventario.html?tab=asignaciones', 'Asignaciones', 'asignaciones']],
    },
    { id: 'comunicaciones', titulo: 'Comunicaciones', icono: 'megafono', items: [['notificaciones', 'notificaciones.html', 'Notificaciones', 'notificaciones']] },
  ];
  const ICONO_MODULO = { panel: 'panel', activos: 'activos', prestamos: 'prestamos', inventario: 'movimientos', notificaciones: 'notificaciones' };

  const raiz = document.documentElement;
  const preferencia = {
    leer(clave) { try { return localStorage.getItem(clave); } catch { return null; } },
    guardar(clave, valor) { try { localStorage.setItem(clave, valor); } catch { /* sin almacenamiento: dura hasta recargar */ } },
  };
  const esMovil = () => window.matchMedia('(max-width: 900px)').matches;

  /** "maria.perez@x.co" -> "MP"; "79123456@x.co" -> "79". */
  function iniciales(correo) {
    const partes = String(correo || '').split('@')[0].split(/[._-]+/).filter(Boolean);
    const texto = partes.length > 1 ? partes[0][0] + partes[1][0] : (partes[0] || '').slice(0, 2);
    return texto.toUpperCase() || '—';
  }

  async function actualizarContador() {
    const contadores = document.querySelectorAll('.contador');
    if (!contadores.length) return;
    try {
      const n = await api.notificacionesNoLeidas();
      contadores.forEach((c) => { c.textContent = n > 99 ? '99+' : String(n); c.hidden = !n; });
    } catch { contadores.forEach((c) => { c.hidden = true; }); }
  }

  /** Marca la entrada del menú que corresponde a la vista actual. */
  function marcarNav(id) {
    document.querySelectorAll('.nav a[data-pagina]').forEach((a) => {
      if (a.dataset.pagina === id) a.setAttribute('aria-current', 'page');
      else a.removeAttribute('aria-current');
    });
  }

  function pintarRiel(riel, actual, u) {
    let cerradas = [];
    try { cerradas = JSON.parse(preferencia.leer('eter.secciones')) || []; } catch { /* preferencia dañada: todo abierto */ }
    const tieneActual = (s) => s.items.some(([id]) => id === actual);
    riel.innerHTML = html`
      <a class="marca" href="index.html" aria-label="ETER, ir al dashboard">
        <span class="marca-icono">${logo()}</span>
        <span class="marca-texto"><span class="marca-nombre">ETER</span><span class="marca-sub">Sistema de Gestión</span></span>
      </a>
      <nav class="nav" aria-label="Módulos">
        ${MENU.map((s) => {
          const abierta = tieneActual(s) || !cerradas.includes(s.id);
          return html`
            <div class="nav-seccion" data-seccion="${s.id}" data-abierta="${String(abierta)}">
              <button type="button" class="nav-titulo" aria-expanded="${String(abierta)}" aria-controls="nav-${s.id}">
                ${icono(s.icono)}<span>${s.titulo}</span>${icono('abajo', 'nav-flecha')}
              </button>
              <div class="nav-items" id="nav-${s.id}"><div>
                ${s.items.map(([id, href, txt, ic]) => html`
                  <a href="${href}" data-pagina="${id}" title="${txt}">
                    ${icono(ic)}<span>${txt}</span>${id === 'notificaciones' ? html`<span class="contador" hidden></span>` : ''}
                  </a>`)}
              </div></div>
            </div>`;
        })}
      </nav>
      <div class="riel-pie">
        <span class="avatar" aria-hidden="true">${iniciales(u.email)}</span>
        <div class="riel-usuario">
          <strong title="${u.email || ''}">${u.email || 'Sin usuario'}</strong>
          <span>${(u.roles || []).join(', ') || 'Sin rol'}</span>
        </div>
      </div>`;
    marcarNav(actual);
    riel.querySelector('.nav').addEventListener('click', (ev) => {
      const b = ev.target.closest('.nav-titulo');
      if (!b) return;
      const seccion = b.closest('.nav-seccion');
      const abierta = seccion.dataset.abierta !== 'true';
      seccion.dataset.abierta = String(abierta);
      b.setAttribute('aria-expanded', String(abierta));
      const ids = [...riel.querySelectorAll('.nav-seccion[data-abierta="false"]')].map((x) => x.dataset.seccion);
      preferencia.guardar('eter.secciones', JSON.stringify(ids));
    });
  }

  function pintarSuperior(riel, u) {
    const barra = document.createElement('header');
    barra.className = 'superior';
    barra.innerHTML = html`
      <button type="button" class="icono-boton" data-menu></button>
      <nav class="migas" id="migas" aria-label="Ruta"></nav>
      <div class="superior-derecha">
        ${sesion.demo ? html`<span class="chip chip--aviso">Demostración</span>` : ''}
        <span class="chip chip--universidad" title="Universidad del token">${icono('universidad')}<span>NIT ${u.nitUniv || '—'}</span></span>
        <button type="button" class="icono-boton" data-tema></button>
        <a class="icono-boton" href="notificaciones.html" aria-label="Notificaciones" title="Notificaciones">
          ${icono('notificaciones')}<span class="insignia contador" hidden></span>
        </a>
        <div class="usuario">
          <button type="button" class="usuario-boton" aria-expanded="false" aria-controls="menu-usuario" aria-label="Menú de usuario">
            <span class="avatar" aria-hidden="true">${iniciales(u.email)}</span>${icono('abajo')}
          </button>
          <div class="usuario-menu" id="menu-usuario" hidden>
            <div class="usuario-cabeza">
              <span class="avatar" aria-hidden="true">${iniciales(u.email)}</span>
              <div class="usuario-datos">
                <strong title="${u.email || ''}">${u.email || 'Sin usuario'}</strong>
                <span>${(u.roles || []).join(', ') || 'Sin rol'} · NIT ${u.nitUniv || '—'}</span>
              </div>
            </div>
            <div class="usuario-separador"></div>
            ${sesion.demo ? html`<button type="button" class="usuario-accion" data-reiniciar>${icono('reiniciar')}<span>Restablecer datos</span></button>` : ''}
            <a class="usuario-accion" href="ingresar.html?cambiar=1">${icono('enlace')}<span>${sesion.demo ? 'Conectar al gateway' : 'Cambiar token o gateway'}</span></a>
            <div class="usuario-separador"></div>
            <button type="button" class="usuario-accion usuario-accion--salir" data-salir>${icono('salir')}<span>Cerrar sesión</span></button>
          </div>
        </div>
      </div>`;
    riel.after(barra);

    const velo = document.createElement('div');
    velo.className = 'velo';
    barra.after(velo);

    // Hamburguesa: colapsa el menú en escritorio y abre el cajón en móvil.
    const btnMenu = barra.querySelector('[data-menu]');
    const pintarMenu = () => {
      const movil = esMovil();
      const abierto = movil ? raiz.dataset.cajon === 'abierto' : raiz.dataset.menu !== 'colapsado';
      const etiqueta = movil ? (abierto ? 'Cerrar menú' : 'Abrir menú') : (abierto ? 'Colapsar menú' : 'Expandir menú');
      btnMenu.innerHTML = icono(abierto && !movil ? 'menuCerrar' : 'menu');
      btnMenu.setAttribute('aria-label', etiqueta);
      btnMenu.setAttribute('aria-expanded', String(abierto));
      btnMenu.title = etiqueta;
    };
    const cerrarCajon = () => { delete raiz.dataset.cajon; pintarMenu(); };
    btnMenu.addEventListener('click', () => {
      if (esMovil()) {
        if (raiz.dataset.cajon) delete raiz.dataset.cajon; else raiz.dataset.cajon = 'abierto';
      } else if (raiz.dataset.menu) {
        delete raiz.dataset.menu;
        preferencia.guardar('eter.menu', 'expandido');
      } else {
        raiz.dataset.menu = 'colapsado';
        preferencia.guardar('eter.menu', 'colapsado');
      }
      pintarMenu();
    });
    velo.addEventListener('click', cerrarCajon);
    window.matchMedia('(max-width: 900px)').addEventListener('change', cerrarCajon);
    pintarMenu();

    // Tema
    const btnTema = barra.querySelector('[data-tema]');
    const pintarTema = () => {
      const oscuro = raiz.dataset.tema === 'oscuro';
      const etiqueta = oscuro ? 'Cambiar a tema claro' : 'Cambiar a tema oscuro';
      btnTema.innerHTML = icono(oscuro ? 'sol' : 'luna');
      btnTema.setAttribute('aria-label', etiqueta);
      btnTema.title = etiqueta;
    };
    btnTema.addEventListener('click', () => {
      raiz.dataset.tema = raiz.dataset.tema === 'oscuro' ? 'claro' : 'oscuro';
      preferencia.guardar('eter.tema', raiz.dataset.tema);
      pintarTema();
    });
    pintarTema();

    // Menú de usuario
    const btnUsuario = barra.querySelector('.usuario-boton');
    const menu = barra.querySelector('.usuario-menu');
    const alternar = (abrirlo) => {
      menu.hidden = !abrirlo;
      btnUsuario.setAttribute('aria-expanded', String(abrirlo));
    };
    btnUsuario.addEventListener('click', () => alternar(menu.hidden));
    document.addEventListener('click', (ev) => {
      if (!menu.hidden && !ev.target.closest('.usuario')) alternar(false);
    });
    document.addEventListener('keydown', (ev) => {
      if (ev.key !== 'Escape') return;
      if (!menu.hidden) { alternar(false); btnUsuario.focus(); }
      if (raiz.dataset.cajon) cerrarCajon();
    });
    barra.querySelector('[data-salir]').addEventListener('click', () => {
      sesion.cerrar();
      location.href = 'ingresar.html';
    });
    barra.querySelector('[data-reiniciar]')?.addEventListener('click', async () => {
      alternar(false);
      const ok = await confirmar({ titulo: 'Restablecer datos', texto: 'Se pierden los cambios hechos en la demostración y vuelven los datos de ejemplo.', accion: 'Restablecer', peligro: true });
      if (ok) { api.reiniciarDatos(); location.reload(); }
    });
  }

  /**
   * La ruta vive en .folio dentro de .cabecera y se muestra en la barra superior.
   * Algunas páginas reescriben .cabecera (activo.js); el observador repone el
   * ícono del módulo y vuelve a copiar la ruta cada vez.
   */
  function vigilarCabecera(pagina) {
    const migas = document.getElementById('migas');
    const cabecera = document.querySelector('.cabecera');
    if (!cabecera) {
      migas.innerHTML = html`<a href="index.html">Patrimonio</a> / ${MENU.flatMap((s) => s.items).find(([id]) => id === pagina)?.[2] || ''}`;
      return;
    }
    const sincronizar = () => {
      const folio = cabecera.querySelector('.folio');
      if (folio) migas.innerHTML = folio.innerHTML;
      if (!cabecera.querySelector('.cabecera-icono')) {
        const caja = document.createElement('span');
        caja.className = 'cabecera-icono';
        caja.innerHTML = icono(ICONO_MODULO[pagina] || 'panel');
        cabecera.prepend(caja);
      }
    };
    sincronizar();
    new MutationObserver(sincronizar).observe(cabecera, { childList: true });
  }

  /**
   * Dibuja el armazón. Devuelve false si no hay sesión (y redirige a ingresar.html).
   * ?demo=1 en la URL abre la página directamente en modo demostración.
   */
  function montar({ pagina }) {
    if (param('demo') === '1') sesion.iniciarDemo();
    if (!sesion.activa()) {
      const aqui = location.pathname.split('/').pop() + location.search;
      location.replace(`ingresar.html?volver=${encodeURIComponent(aqui)}`);
      return false;
    }
    if (sesion.vencida()) {
      sesion.cerrar();
      location.replace('ingresar.html?motivo=vencida');
      return false;
    }
    const u = sesion.usuario() || {};
    const riel = document.getElementById('riel');
    if (riel) {
      const actual = pagina === 'inventario' && param('tab') === 'asignaciones' ? 'asignaciones' : pagina;
      pintarRiel(riel, actual, u);
      pintarSuperior(riel, u);
      vigilarCabecera(pagina);
    }
    document.querySelectorAll('i[data-icono]').forEach((i) => { i.outerHTML = String(icono(i.dataset.icono)); });
    window.addEventListener('eter:avisos', (ev) => {
      const servicios = [...new Set(ev.detail.map((e) => e.servicio).filter(Boolean))];
      toast(servicios.length
        ? `Parte de la información no cargó: ${servicios.join(', ')} no respondió.`
        : 'Parte de la información no cargó.', { tipo: 'aviso', ms: 7000 });
    });
    actualizarContador();
    return true;
  }

  ETER.ui = {
    html, crudo, esc, icono, logo, fmt,
    ESTADO_ACTIVO, ESTADO_PRESTAMO, TIPO_MOVIMIENTO, TIPO_NOTIFICACION,
    estadoActivo, estadoPrestamo, selloPrestamo, placa, codigo, urlActivo, enlaceActivo,
    mensajeError, alerta, error, marcarErrores, limpiarErrores, toast,
    abrir, cerrar, confirmar, datosForm, aLocalDateTime, aInputLocal, fechaInput, ocupado,
    cargandoFilas, cargandoBloque, vacio, param, demorar, soloAdmin, SIN_PERMISO,
    paginador, montar, marcarNav, actualizarContador,
  };
})();
