// Bandeja de notificaciones con suscripción en vivo.
(function () {
  'use strict';
  const { ui, api, sesion } = ETER;
  if (!ui.montar({ pagina: 'notificaciones' })) return;

  const html = ui.html;
  const $ = (id) => document.getElementById(id);

  document.querySelectorAll('i[data-icono]').forEach((i) => { i.outerHTML = String(ui.icono(i.dataset.icono)); });

  const TONO = { PRESTAMO_VENCIDO: 'peligro', PRESTAMO_DEVUELTO: 'ok', PRESTAMO_CREADO: 'info' };
  const ESTADO_VIVO = {
    conectando: ['Conectando…', 'Abriendo la conexión en vivo con el gateway.'],
    'en-vivo': ['En vivo', 'Conectada. El gateway revisa tu bandeja en notificaciones-service cada 5 s y empuja por WebSocket solo las nuevas.'],
    reconectando: ['Reconectando…', 'Se perdió la conexión con el gateway. Se reintenta con espera creciente, hasta 30 s entre intentos.'],
    cerrada: ['Sin conexión en vivo', 'La suscripción está cerrada. Recarga la página para abrirla de nuevo.'],
  };

  const lista = $('bandeja');
  const avisoB = $('aviso-bandeja');
  const nuevasArriba = $('nuevas-arriba');
  const paginacion = $('paginacion');
  const btnTodas = $('btn-todas');
  const btnAnterior = $('pag-anterior');
  const btnSiguiente = $('pag-siguiente');
  const anuncio = $('anuncio');
  const admin = sesion.esAdmin();

  $('destinatario').textContent = sesion.usuario()?.email || 'sin usuario';
  ui.soloAdmin(btnTodas);
  if (sesion.demo) {
    $('nota-origen').insertAdjacentHTML('beforeend', String(html`
      <span class="nota-demo">En demostración: registra en Préstamos un préstamo para el documento <span class="mono">79123456</span> y el aviso llega aquí en unos segundos, sin recargar.</span>`));
  }

  const est = {
    filtro: 'todas',
    page: 0,
    size: 20,
    total: 0,
    totalTodas: null,
    noLeidas: 0,
    items: [],
    cargando: false,
    peticion: 0,
    cola: [],
    nuevasFuera: 0,
  };

  // Pintado
  const item = (n, { nueva = false } = {}) => html`
    <li class="notif${n.leida ? ' notif--leida' : ''}${nueva ? ' notif--nueva' : ''}" data-id="${n.idNotificacion}" tabindex="-1">
      <span class="notif-marca" aria-hidden="true"></span>
      <div class="notif-cuerpo">
        <p class="notif-linea">
          <span class="estado estado--${TONO[n.tipo] || 'neutro'}">${ui.TIPO_NOTIFICACION[n.tipo] || n.tipo}</span>
          <span class="sr">${n.leida ? 'Leída.' : 'Sin leer.'}</span>
          <time class="notif-hora" datetime="${n.creadaEn}" title="${ui.fmt.fechaHora(n.creadaEn)}" data-relativa>${ui.fmt.relativa(n.creadaEn)}</time>
        </p>
        <h3 class="notif-titulo">${n.titulo}</h3>
        <p class="notif-mensaje">${n.mensaje}</p>
        <p class="notif-meta">
          ${n.placaInventario ? ui.enlaceActivo(n.placaInventario) : ''}
          ${n.leida && n.leidaEn
            ? html`<span>Leída <time datetime="${n.leidaEn}" title="${ui.fmt.fechaHora(n.leidaEn)}" data-relativa>${ui.fmt.relativa(n.leidaEn)}</time></span>`
            : ''}
          ${n.eventId
            ? html`<span class="mono notif-evento" title="eventId del evento de Kafka: ${n.eventId}">evt ${String(n.eventId).slice(0, 8)}</span>`
            : ''}
        </p>
      </div>
      <div class="notif-acciones">
        ${n.leida ? '' : html`<button type="button" class="boton boton--sm" data-marcar${ui.crudo(admin ? '' : ` disabled title="${ui.esc(ui.SIN_PERMISO)}"`)}>${ui.icono('check')}<span>Marcar como leída</span></button>`}
      </div>
    </li>`;

  const esqueleto = (n = 4) => ui.crudo(Array.from({ length: n }, (_, i) => `
    <li class="notif notif--cargando" aria-hidden="true">
      <span></span>
      <div class="notif-cuerpo">
        <span class="esqueleto" style="width:118px"></span>
        <span class="esqueleto" style="width:${58 - i * 6}%;height:18px"></span>
        <span class="esqueleto" style="width:${86 - i * 8}%"></span>
      </div>
    </li>`).join(''));

  function contadores() {
    $('cuenta-sin-leer').textContent = String(est.noLeidas);
    $('cuenta-todas').textContent = est.totalTodas == null ? '' : String(est.totalTodas);
    btnTodas.disabled = !admin || !est.noLeidas;
  }

  function pintarPaginacion() {
    if (!est.total) { paginacion.hidden = true; return; }
    paginacion.hidden = false;
    const desde = est.page * est.size + 1;
    const hasta = est.page * est.size + est.items.length;
    $('rango').textContent = `${desde}–${hasta} de ${est.total}`;
    btnAnterior.disabled = est.page === 0;
    btnSiguiente.disabled = hasta >= est.total;
  }

  function pintarVacio() {
    lista.hidden = true;
    lista.innerHTML = '';
    avisoB.innerHTML = est.filtro === 'sin-leer'
      ? ui.vacio({
        titulo: 'Bandeja al día',
        texto: 'No tienes notificaciones sin leer. Si llega una mientras esta página está abierta, aparece aquí sin recargar.',
      })
      : ui.vacio({
        titulo: 'Bandeja vacía',
        texto: 'Aún no tienes notificaciones. Llegan cuando se registra, se devuelve o vence un préstamo a tu nombre.',
      });
  }

  function pintar() {
    contadores();
    pintarPaginacion();
    if (!est.items.length) { pintarVacio(); return; }
    avisoB.innerHTML = '';
    lista.hidden = false;
    lista.innerHTML = html`${est.items.map((n) => item(n))}`;
  }

  // Carga
  async function cargar({ silencioso = false } = {}) {
    const mia = ++est.peticion;
    est.cargando = true;
    lista.setAttribute('aria-busy', 'true');
    if (!silencioso) {
      avisoB.innerHTML = '';
      lista.hidden = false;
      lista.innerHTML = esqueleto();
    }
    try {
      const r = await api.notificaciones({ page: est.page, size: est.size, soloNoLeidas: est.filtro === 'sin-leer' });
      if (mia !== est.peticion) return;
      // Página fuera de rango: vuelve a la última.
      if (!r.items.length && est.page > 0 && r.totalElements > 0) {
        est.page = Math.max(0, Math.ceil(r.totalElements / est.size) - 1);
        est.cargando = false;
        cargar({ silencioso });
        return;
      }
      est.items = r.items;
      est.total = r.totalElements;
      est.noLeidas = r.noLeidas;
      if (est.filtro === 'todas') est.totalTodas = r.totalElements;
      if (est.page === 0) { est.nuevasFuera = 0; nuevasArriba.hidden = true; nuevasArriba.innerHTML = ''; }
      pintar();
    } catch (e) {
      if (mia !== est.peticion) return;
      lista.hidden = true;
      lista.innerHTML = '';
      paginacion.hidden = true;
      ui.error(e, { en: avisoB });
    } finally {
      if (mia === est.peticion) {
        est.cargando = false;
        lista.removeAttribute('aria-busy');
        const pendientes = est.cola.splice(0);
        pendientes.forEach(recibir);
      }
    }
  }

  // En vivo
  function recibir(n) {
    if (!n || !n.idNotificacion) return;
    if (est.cargando) { est.cola.push(n); return; }
    if (est.items.some((x) => x.idNotificacion === n.idNotificacion)) return;

    if (!n.leida) est.noLeidas += 1;
    if (est.totalTodas != null) est.totalTodas += 1;
    const entraEnVista = est.filtro === 'todas' || !n.leida;
    if (entraEnVista) est.total += 1;

    if (entraEnVista && est.page === 0) {
      if (!est.items.length) { avisoB.innerHTML = ''; lista.hidden = false; }
      est.items.unshift(n);
      lista.insertAdjacentHTML('afterbegin', String(item(n, { nueva: true })));
      if (est.items.length > est.size) { est.items.pop(); lista.lastElementChild?.remove(); }
      const li = lista.firstElementChild;
      setTimeout(() => li.classList.remove('notif--nueva'), 4000);
    } else if (entraEnVista) {
      est.nuevasFuera += 1;
      nuevasArriba.hidden = false;
      nuevasArriba.innerHTML = html`
        <div class="alerta alerta--info">
          <strong>${est.nuevasFuera === 1 ? 'Llegó una notificación nueva' : `Llegaron ${est.nuevasFuera} notificaciones nuevas`}</strong>
          <span>Están en la primera página. <button type="button" class="boton boton--sm" data-ir-inicio>Ir a la primera página</button></span>
        </div>`;
    }

    contadores();
    pintarPaginacion();
    ui.toast(n.titulo, { tipo: n.tipo === 'PRESTAMO_VENCIDO' ? 'aviso' : 'info', ms: 6000 });
    anuncio.textContent = `Nueva notificación: ${n.titulo}. ${est.noLeidas} sin leer.`;
    ui.actualizarContador();
  }

  function pintarEstado(estado) {
    const [texto, detalle] = ESTADO_VIVO[estado] || [estado, ''];
    const caja = $('en-vivo');
    caja.dataset.estado = estado;
    caja.title = detalle;
    $('en-vivo-texto').textContent = texto;
    $('vivo-nota').textContent = estado === 'en-vivo' || estado === 'conectando'
      ? 'El gateway revisa la bandeja cada 5 s y empuja aquí solo las nuevas.'
      : detalle;
  }

  let ultimoErrorVivo = 0;
  function errorVivo(e) {
    if (e?.code === 'UNAUTHENTICATED') { ui.error(e); return; }
    if (Date.now() - ultimoErrorVivo < 30000) return; // no repetir el aviso en cada reintento
    ultimoErrorVivo = Date.now();
    ui.toast(`La conexión en vivo falló: ${e?.message || 'error desconocido'}. La bandeja sigue disponible; vuelve a consultar para ver las nuevas.`, { tipo: 'aviso', ms: 8000 });
  }

  let cerrarSuscripcion = null;
  function suscribir() {
    if (cerrarSuscripcion) return;
    cerrarSuscripcion = api.suscribirNotificaciones({ onNext: recibir, onError: errorVivo, onEstado: pintarEstado });
  }
  window.addEventListener('pagehide', () => {
    if (cerrarSuscripcion) { cerrarSuscripcion(); cerrarSuscripcion = null; }
  });
  window.addEventListener('pageshow', (ev) => {
    if (ev.persisted) { suscribir(); cargar({ silencioso: true }); }
  });

  // Acciones
  $('segmentos').addEventListener('click', (ev) => {
    const b = ev.target.closest('[data-filtro]');
    if (!b || b.dataset.filtro === est.filtro) return;
    est.filtro = b.dataset.filtro;
    est.page = 0;
    $('segmentos').querySelectorAll('[data-filtro]').forEach((x) => x.setAttribute('aria-pressed', String(x === b)));
    cargar();
  });

  btnAnterior.addEventListener('click', () => { if (est.page > 0) { est.page -= 1; cargar(); lista.scrollIntoView({ block: 'nearest' }); } });
  btnSiguiente.addEventListener('click', () => { est.page += 1; cargar(); lista.scrollIntoView({ block: 'nearest' }); });
  nuevasArriba.addEventListener('click', (ev) => {
    if (!ev.target.closest('[data-ir-inicio]')) return;
    est.page = 0;
    cargar();
  });

  lista.addEventListener('click', async (ev) => {
    const btn = ev.target.closest('[data-marcar]');
    if (!btn) return;
    const li = btn.closest('.notif');
    const id = li.dataset.id;
    ui.ocupado(btn, true);
    try {
      const r = await api.marcarNotificacionLeida(id);
      est.noLeidas = Math.max(0, est.noLeidas - 1);
      ui.actualizarContador();
      anuncio.textContent = 'Notificación marcada como leída.';
      if (est.filtro === 'sin-leer') {
        const vecina = li.nextElementSibling || li.previousElementSibling;
        est.items = est.items.filter((x) => x.idNotificacion !== id);
        est.total = Math.max(0, est.total - 1);
        li.remove();
        contadores();
        if (est.page * est.size + est.items.length < est.total || (!est.items.length && est.total)) {
          await cargar({ silencioso: true }); // trae la siguiente para completar la página
          lista.querySelector('[data-marcar]')?.focus();
        } else if (!est.items.length) {
          pintar();
        } else {
          pintarPaginacion();
          (vecina?.querySelector('[data-marcar]') || vecina)?.focus();
        }
      } else {
        const i = est.items.findIndex((x) => x.idNotificacion === id);
        est.items[i] = { ...est.items[i], ...r, leida: true, leidaEn: r?.leidaEn || ETER.isoLocal() };
        li.outerHTML = String(item(est.items[i]));
        contadores();
        lista.querySelector(`[data-id="${CSS.escape(id)}"]`)?.focus();
      }
    } catch (e) {
      ui.ocupado(btn, false);
      ui.error(e);
    }
  });

  btnTodas.addEventListener('click', async () => {
    ui.ocupado(btnTodas, true);
    try {
      await api.marcarTodasNotificacionesLeidas();
      ui.toast('Listo: no quedan notificaciones sin leer.');
      anuncio.textContent = 'Todas las notificaciones quedaron leídas.';
      ui.actualizarContador();
      est.page = 0;
      await cargar({ silencioso: true });
    } catch (e) {
      ui.error(e);
    } finally {
      ui.ocupado(btnTodas, false);
      contadores();
    }
  });

  // Recalcula los tiempos relativos cada 30 s.
  setInterval(() => {
    document.querySelectorAll('time[data-relativa]').forEach((t) => { t.textContent = ui.fmt.relativa(t.getAttribute('datetime')); });
  }, 30000);

  // Arranque: primero la suscripción para no perder avisos.
  suscribir();
  cargar();
})();
