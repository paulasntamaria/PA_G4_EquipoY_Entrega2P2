/*
 * ETER · ingresar.html
 *
 * No hay servicio de login: el JWT (HS256) se genera con
 * python tools/generar_token.py y aquí solo se guarda y se valida contra el
 * gateway con la query me. Esta pantalla no lleva riel, así que no llama a
 * ui.montar.
 */
(function () {
  'use strict';
  const { ui, api, sesion } = ETER;

  // Solo se vuelve a páginas de la app (nada de URLs absolutas ni rutas raras).
  const PAGINA_APP = /^(?:index|activos|activo|prestamos|inventario|notificaciones)\.html(?:[?#][^\s\\]*)?$/;
  const destino = () => {
    const v = ui.param('volver');
    return v && PAGINA_APP.test(v) ? v : 'index.html';
  };

  let motivo = ui.param('motivo');
  const cambiar = ui.param('cambiar') === '1';

  if (sesion.activa() && sesion.vencida()) {
    sesion.cerrar();
    motivo = motivo || 'vencida';
  }
  if (sesion.activa() && !cambiar && !motivo) {
    location.replace(destino());
    return;
  }

  const form = document.getElementById('form-ingreso');
  const campoToken = document.getElementById('token');
  const campoGateway = document.getElementById('gateway');
  const lectura = document.getElementById('token-lectura');
  const aviso = document.getElementById('aviso');
  const btnIngresar = document.getElementById('btn-ingresar');
  const btnDemo = document.getElementById('btn-demo');

  campoGateway.value = sesion.gateway;
  campoGateway.placeholder = 'http://localhost:4000/graphql';

  // ── Aviso de llegada ──────────────────────────────────────────────────────
  if (motivo === 'vencida') {
    aviso.innerHTML = ui.html`
      <div class="alerta alerta--aviso" role="status">
        <strong>Tu token venció</strong>
        <span>Genera otro con <code>python tools/generar_token.py</code> y pégalo abajo.</span>
      </div>`;
  } else if (motivo === 'sesion') {
    aviso.innerHTML = ui.html`
      <div class="alerta" role="alert">
        <strong>El gateway rechazó el token</strong>
        <span>No lo firmó el secreto del ecosistema (<code>ETER_JWT_SECRET</code>) o ya venció. Pega uno nuevo.</span>
      </div>`;
  } else if (cambiar && sesion.demo) {
    aviso.innerHTML = ui.html`
      <div class="alerta alerta--info" role="status">
        <strong>Estás en la demostración</strong>
        <span>Pega un token para trabajar contra el gateway, o <a href="index.html">vuelve al panel de demostración</a>.</span>
      </div>`;
  } else if (cambiar && sesion.activa()) {
    const u = sesion.usuario() || {};
    aviso.innerHTML = ui.html`
      <div class="alerta alerta--info" role="status">
        <strong>Sesión abierta como ${u.email || 'usuario sin correo'}</strong>
        <span>Cambia el token o la URL del gateway, o <a href="${destino()}">vuelve sin cambiar nada</a>.</span>
      </div>`;
    campoToken.value = sesion.token || '';
  }

  // ── Lectura en vivo de los claims ─────────────────────────────────────────
  const limpio = (texto) => String(texto || '').trim().replace(/^Bearer\s+/i, '');
  const venceEn = (c) => (c && c.exp ? new Date(c.exp * 1000) : null);
  const esAdmin = (roles) => roles.includes('ADMIN') || roles.includes('ROLE_ADMIN');

  function leerToken() {
    const texto = limpio(campoToken.value);
    if (!texto) {
      lectura.hidden = true;
      lectura.innerHTML = '';
      return;
    }
    lectura.hidden = false;
    const c = sesion.claims(texto);
    if (!c) {
      lectura.innerHTML = ui.html`
        <div class="alerta alerta--aviso">
          <strong>No parece un JWT</strong>
          <span>Un JWT son tres bloques en base64 separados por puntos: cabecera, claims y firma. Revisa que lo hayas copiado completo.</span>
        </div>`;
      return;
    }
    if (!sesion.tokenValido(texto)) {
      const faltan = ['sub', 'nitUniv'].filter((k) => !c[k]);
      lectura.innerHTML = ui.html`
        <div class="alerta alerta--aviso">
          <strong>Es un JWT, pero no de ETER</strong>
          <span>Le falta ${faltan.join(' y ')}. Los tokens de ETER llevan el correo en sub, el NIT de la universidad en nitUniv y los roles.</span>
        </div>`;
      return;
    }
    const roles = Array.isArray(c.roles) ? c.roles : [];
    const vence = venceEn(c);
    const vencido = !!(vence && vence.getTime() < Date.now());
    lectura.innerHTML = ui.html`
      <p class="lectura-cabeza"><span>Leído del token</span><span>Sin verificar la firma: eso lo hace el gateway.</span></p>
      <dl>
        <div><dt>Correo (sub)</dt><dd>${c.sub}</dd></div>
        <div><dt>NIT universidad</dt><dd class="mono">${c.nitUniv}</dd></div>
        <div><dt>Roles</dt><dd>${roles.length ? roles.join(', ') : 'Ninguno'}</dd></div>
        <div><dt>${vencido ? 'Venció el' : 'Vence el'}</dt><dd class="${vencido ? 'vencio' : ''}">${vence ? ui.fmt.fechaHora(vence.toISOString()) : 'Sin fecha de vencimiento'}</dd></div>
      </dl>
      ${vencido
        ? ui.html`<p class="lectura-nota lectura-nota--peligro">El gateway va a rechazar este token. Genera uno nuevo.</p>`
        : !esAdmin(roles)
          ? ui.html`<p class="lectura-nota">Sin rol ADMIN: podrás consultar, pero no registrar activos, préstamos ni movimientos.</p>`
          : ''}`;
  }

  campoToken.addEventListener('input', () => {
    ui.limpiarErrores(form);
    leerToken();
  });
  campoGateway.addEventListener('input', () => ui.limpiarErrores(form));
  leerToken();

  // ── Ingresar ──────────────────────────────────────────────────────────────
  function validar(token, url) {
    const errores = [];
    if (!token) {
      errores.push({ campo: 'token', mensaje: 'Pega el token que imprime generar_token.py.' });
    } else if (!sesion.tokenValido(token)) {
      errores.push({ campo: 'token', mensaje: 'No es un JWT de ETER: necesita los claims sub y nitUniv.' });
    } else {
      const vence = venceEn(sesion.claims(token));
      if (vence && vence.getTime() < Date.now()) errores.push({ campo: 'token', mensaje: `Venció el ${ui.fmt.fechaHora(vence.toISOString())}. Genera uno nuevo.` });
    }
    if (!/^https?:\/\/\S+$/i.test(url)) {
      errores.push({ campo: 'gateway', mensaje: 'Debe empezar por http:// o https://, por ejemplo http://localhost:4000/graphql.' });
    }
    return errores;
  }

  form.addEventListener('submit', async (ev) => {
    ev.preventDefault();
    ui.limpiarErrores(form);
    aviso.innerHTML = '';

    const token = limpio(campoToken.value);
    const url = campoGateway.value.trim() || sesion.gateway;
    const errores = validar(token, url);
    if (errores.length) {
      ui.marcarErrores(form, errores);
      form.querySelector('[aria-invalid="true"]')?.focus();
      return;
    }

    const texto = btnIngresar.textContent;
    ui.ocupado(btnIngresar, true);
    btnIngresar.textContent = 'Verificando con el gateway…';
    sesion.iniciar(token, url);
    try {
      await api.me();
      location.replace(destino());
    } catch (e) {
      // No se usa ui.error: en UNAUTHENTICATED redirigiría a esta misma página.
      sesion.cerrar();
      aviso.innerHTML = e && e.code === 'UNAUTHENTICATED'
        ? ui.html`
          <div class="alerta" role="alert">
            <strong>El gateway rechazó el token</strong>
            <span>${url} respondió «${e.message}». Revisa que el gateway use el mismo <code>ETER_JWT_SECRET</code> con el que se firmó el token.</span>
          </div>`
        : ui.alerta(e);
      ui.ocupado(btnIngresar, false);
      btnIngresar.textContent = texto;
      aviso.scrollIntoView({ block: 'nearest' });
    }
  });

  btnDemo.addEventListener('click', () => {
    sesion.iniciarDemo();
    location.replace(destino());
  });
})();
