// Armazón de la aplicación (ETER.ui). Para HTML con datos use ui.html o ui.esc.
(function () {
  'use strict';
  const ETER = window.ETER;
  const { sesion, api } = ETER;

  // HTML seguro
  const esc = (v) => String(v ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]);
  class Crudo { constructor(h) { this.h = h; } toString() { return this.h; } }
  const crudo = (h) => new Crudo(String(h ?? ''));
  const valor = (v) => (v instanceof Crudo ? v.h : Array.isArray(v) ? v.map(valor).join('') : v === false || v == null ? '' : esc(v));
  function html(partes, ...valores) {
    let out = partes[0];
    valores.forEach((v, i) => { out += valor(v) + partes[i + 1]; });
    return new Crudo(out);
  }

  // Iconos: nombre interno -> Material Symbols Outlined (https://fonts.google.com/icons)
  const ICONOS = {
    panel: 'space_dashboard',
    activos: 'sell',
    prestamos: 'sync_alt',
    inventario: 'inventory',
    notificaciones: 'notifications',
    casa: 'home',
    caja: 'package_2',
    movimientos: 'swap_vert',
    asignaciones: 'assignment_ind',
    megafono: 'campaign',
    universidad: 'school',
    menu: 'menu',
    menuCerrar: 'menu_open',
    sol: 'light_mode',
    luna: 'dark_mode',
    abajo: 'expand_more',
    reiniciar: 'restart_alt',
    enlace: 'link',
    mas: 'add',
    buscar: 'search',
    salir: 'logout',
    cerrar: 'close',
    flecha: 'arrow_forward',
    volver: 'arrow_back',
    reloj: 'schedule',
    check: 'check_circle',
    alerta: 'warning',
    editar: 'edit',
    baja: 'delete',
    filtro: 'filter_alt',
    usuario: 'person',
    senal: 'sensors',
    devolver: 'assignment_return',
    llave: 'key',
    servidor: 'dns',
  };
  const icono = (nombre, clase = '') =>
    crudo(`<span class="icono ${clase}" aria-hidden="true">${ICONOS[nombre] || 'help'}</span>`);
  /** Isotipo de ETER: versión a color en tema claro y blanca en tema oscuro (assets/img). */
  const logo = () => crudo(`
    <img class="solo-claro" src="assets/img/eter-isotipo.png" alt="" width="40" height="40">
    <img class="solo-oscuro" src="assets/img/eter-isotipo-oscuro.png" alt="" width="40" height="40">`);

  // Formato
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
    /** "2026-10-28 08:00" */
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

  // Dominio
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

  // Código de barras decorativo.
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

  /** Etiqueta de inventario. */
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

  // Errores
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
  /** Caja .alerta con el error. */
  function alerta(err) {
    const { titulo, detalle } = mensajeError(err);
    return html`<div class="alerta" role="alert"><strong>${titulo}</strong><span>${detalle}</span>${
      err?.errores?.length ? html`<ul>${err.errores.map((e) => html`<li><span class="mono">${e.campo}</span>: ${e.mensaje}</li>`)}</ul>` : ''
    }</div>`;
  }
  /** Muestra el error; si la sesión expiró, vuelve a ingresar.html. */
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
  /** Marca los campos con error y devuelve cuántos encontró. */
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

  // Avisos flotantes
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

  // Diálogos
  const abrir = (dlg) => { dlg.showModal(); dlg.querySelector('[autofocus], input, select, textarea')?.focus(); };
  const cerrar = (dlg) => dlg.close();
  /** Confirmación propia. Devuelve Promise<boolean>. */
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

  // Formularios
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
  /** Hoy + dias, a la hora indicada. */
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

  // Carga y vacío
  const cargandoFilas = (columnas, filas = 5) =>
    crudo(Array.from({ length: filas }, (_, i) =>
      `<tr aria-hidden="true">${Array.from({ length: columnas }, (_, j) =>
        `<td><span class="esqueleto" style="width:${40 + ((i * 7 + j * 13) % 50)}%"></span></td>`).join('')}</tr>`).join(''));
  const cargandoBloque = (lineas = 3) =>
    crudo(`<div class="pila" aria-hidden="true">${Array.from({ length: lineas }, (_, i) =>
      `<span class="esqueleto" style="width:${92 - i * 17}%"></span>`).join('')}</div>`);
  const vacio = ({ titulo, texto, accion }) =>
    html`<div class="vacio"><p class="vacio-titulo">${titulo}</p><p>${texto}</p>${accion || ''}</div>`;

  // Varios
  const param = (nombre) => new URLSearchParams(location.search).get(nombre);
  const demorar = (fn, ms = 250) => { let t; return (...a) => { clearTimeout(t); t = setTimeout(() => fn(...a), ms); }; };
  const SIN_PERMISO = 'Tu rol no permite esta operación. Necesitas el rol ADMIN.';
  /** Deshabilita los botones de escritura si el usuario no es ADMIN. */
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

  // Paginación
  /** Paginación en el cliente; pag.cortar(lista) devuelve la página visible. */
  function paginador(caja, { tamano = 10, tamanos = [10, 25, 50], alCambiar }) {
    let pagina = 1;
    let porPagina = tamano;
    let total = 0;

    /** Páginas a mostrar; null = puntos. */
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

  // Armazón
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
    guardar(clave, valor) { try { localStorage.setItem(clave, valor); } catch { /* sin almacenamiento */ } },
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
        <span class="marca-logo">${logo()}</span>
        <span class="marca-texto"><span class="marca-nombre">ETER</span><span class="marca-sub">Inventario y movimientos</span></span>
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

    // Escritorio: colapsa el menú; móvil: abre el cajón.
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

  /** Mantiene la ruta y el ícono aunque la página reescriba .cabecera. */
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

  /** Dibuja el armazón; devuelve false si no hay sesión. ?demo=1 abre en modo demo. */
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
