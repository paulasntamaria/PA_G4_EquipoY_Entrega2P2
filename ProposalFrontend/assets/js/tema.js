// Aplica el tema y el ancho del menú antes de pintar, para evitar parpadeos.
(function () {
  'use strict';
  const leer = (clave) => {
    try { return localStorage.getItem(clave); } catch { return null; }
  };
  const raiz = document.documentElement;
  if (!raiz.hasAttribute('data-tema-fijo')) {
    raiz.dataset.tema = leer('eter.tema') === 'claro' ? 'claro' : 'oscuro';
  }
  if (leer('eter.menu') === 'colapsado') raiz.dataset.menu = 'colapsado';
})();
