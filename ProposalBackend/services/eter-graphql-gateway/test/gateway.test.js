import assert from 'node:assert/strict';
import { createServer } from 'node:http';
import { after, before, test } from 'node:test';
import jwt from 'jsonwebtoken';
import { createClient } from 'graphql-ws';
import WebSocket from 'ws';

// Microservicios simulados: se levantan antes de cargar el gateway.
const llamadas = [];
let notificaciones = [];
const mock = createServer((req, res) => {
  const url = new URL(req.url, 'http://x');
  llamadas.push(`${req.method} ${url.pathname}${url.search}`);
  const json = (status, cuerpo) => {
    res.writeHead(status, { 'Content-Type': 'application/json' });
    res.end(cuerpo === undefined ? '' : JSON.stringify(cuerpo));
  };
  const activo = { nitUniv: '900123456-1', placaInventario: 'AF-1', descripcion: 'Portatil', codUnspsc: '43211503', codSede: 'SED-01', valorContable: 100.5, estado: 'ACTIVO' };
  const { pathname: p } = url;
  if (p === '/api/v1/activos/activos-fijos' && req.method === 'GET') return json(200, [activo, { ...activo, placaInventario: 'AF-2', estado: 'BAJA' }]);
  if (p === '/api/v1/activos/activos-fijos/900123456-1/AF-1') return json(200, activo);
  if (p === '/api/v1/activos/activos-fijos/900123456-1/NOPE') return json(404, { title: 'No encontrado', detail: 'Activo NOPE no existe' });
  if (p === '/api/v1/activos/prestamos') return json(200, [{ idPrestamo: 'p1', nitUniv: '900123456-1', placaInventario: 'AF-1', numDocumento: 'CC12345', fechaSalida: '2026-01-01T00:00:00', fechaDevolucionPactada: '2026-01-02T00:00:00', estado: 'VIGENTE', vencido: true }]);
  if (p === '/api/v1/notificaciones') return json(200, { items: notificaciones, page: 0, size: 1, totalElements: notificaciones.length, noLeidas: notificaciones.length });
  json(500, { title: 'inesperado' });
});
await new Promise((r) => mock.listen(0, r));
const base = `http://localhost:${mock.address().port}`;
Object.assign(process.env, {
  ETER_ACTIVOS_BASE_URL: base, ETER_PRESTAMOS_BASE_URL: base, ETER_INVENTARIO_BASE_URL: base,
  ETER_NOTIFICACIONES_BASE_URL: base, ETER_POLL_NOTIFICACIONES_MS: '100',
});
const { crearServidor } = await import('../src/server.js');
const { config } = await import('../src/config.js');

const gateway = crearServidor();
await new Promise((r) => gateway.listen(0, r));
const puerto = gateway.address().port;
const token = jwt.sign({ roles: ['ADMIN'], nitUniv: '900123456-1' }, config.jwtSecret, { subject: 'a@b.co', algorithm: 'HS256' });

const gql = async (query, auth = `Bearer ${token}`) => {
  const r = await fetch(`http://localhost:${puerto}/graphql`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...(auth ? { Authorization: auth } : {}) },
    body: JSON.stringify({ query }),
  });
  return r.json();
};

after(() => { gateway.closeAllConnections(); gateway.close(); mock.closeAllConnections(); mock.close(); });

test('sin token no hay datos', async () => {
  const r = await gql('{ me { email } }', null);
  assert.equal(r.errors[0].extensions.code, 'UNAUTHENTICATED');
});

test('me sale del token', async () => {
  const r = await gql('{ me { email nitUniv roles } }');
  assert.deepEqual(r.data.me, { email: 'a@b.co', nitUniv: '900123456-1', roles: ['ADMIN'] });
});

test('solo se consultan los servicios de los campos pedidos', async () => {
  llamadas.length = 0;
  const r = await gql('{ activos(estado: ACTIVO) { placaInventario } }');
  assert.deepEqual(r.data.activos, [{ placaInventario: 'AF-1' }]);
  assert.equal(llamadas.length, 1);
});

test('composicion: prestamo + activo sin N+1', async () => {
  llamadas.length = 0;
  const r = await gql('{ prestamos(soloVencidos: true) { idPrestamo activo { descripcion } } }');
  assert.equal(r.data.prestamos[0].activo.descripcion, 'Portatil');
  assert.equal(llamadas.length, 2);
});

test('resumen agrega varios servicios', async () => {
  const r = await gql('{ resumen { totalActivos prestamosVigentes prestamosVencidos notificacionesNoLeidas } }');
  assert.deepEqual(r.data.resumen, { totalActivos: 2, prestamosVigentes: 1, prestamosVencidos: 1, notificacionesNoLeidas: 0 });
});

test('404 del backend: activo devuelve null, no error', async () => {
  const r = await gql('{ activo(placaInventario: "NOPE") { placaInventario } }');
  assert.equal(r.data.activo, null);
  assert.equal(r.errors, undefined);
});

test('suscripcion por WebSocket emite solo notificaciones nuevas', async () => {
  const cliente = createClient({ url: `ws://localhost:${puerto}/graphql`, webSocketImpl: WebSocket, connectionParams: { Authorization: `Bearer ${token}` } });
  const recibido = new Promise((resolve, reject) => {
    cliente.subscribe(
      { query: 'subscription { notificacionRecibida { idNotificacion titulo } }' },
      { next: (m) => resolve(m.data.notificacionRecibida), error: reject, complete: () => {} },
    );
  });
  setTimeout(() => {
    notificaciones = [{ idNotificacion: 'n1', titulo: 'Vencido', mensaje: 'm', tipo: 'PRESTAMO_VENCIDO', leida: false, creadaEn: '2026-01-01T00:00:00Z' }];
  }, 350);
  assert.deepEqual(await recibido, { idNotificacion: 'n1', titulo: 'Vencido' });
  await cliente.dispose();
});
