/*
 * ETER · preferencias que deben aplicarse antes de pintar.
 *
 * Se carga en <head>, antes de las hojas de estilo, para que la página no
 * parpadee con el tema o el ancho de menú equivocados. El tema oscuro es el
 * predeterminado; data-tema-fijo en <html> lo deja como está (pantalla de ingreso).
 */
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
