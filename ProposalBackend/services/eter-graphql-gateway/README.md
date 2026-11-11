# ETER GraphQL Gateway

Punto de entrada unico para el frontend. Traduce GraphQL a las APIs REST de los
4 microservicios (activos 8087, prestamos 8088, inventario 8089, notificaciones 8090).

- **Query**: el frontend pide solo los campos que necesita. Los campos que viven
  en otro servicio (`ActivoFijo.prestamos`, `Prestamo.activo`, ...) solo se
  consultan si aparecen en la query, y se memoizan por peticion (sin N+1).
- **Mutations**: una por cada POST/PUT/PATCH/DELETE de los microservicios.
- **Subscription**: `notificacionRecibida` por WebSocket (`graphql-ws`).
- **Seguridad**: el gateway valida el JWT (HS256) y lo reenvia; cada microservicio
  conserva sus reglas de rol (ADMIN para escribir) y de tenant. `nitUniv` nunca lo
  manda el cliente: sale del token.

## Ejecutar

```bash
npm install
npm start                 # http://localhost:4000/graphql
npm test
# o con todo el ecosistema:  docker compose up -d --build   (servicio graphql-gateway)
```

Variables: `PORT`, `ETER_JWT_SECRET`, `ETER_{ACTIVOS,PRESTAMOS,INVENTARIO,NOTIFICACIONES}_BASE_URL`,
`ETER_DOWNSTREAM_TIMEOUT_MS`, `ETER_POLL_NOTIFICACIONES_MS`.

## Ejemplos

Header en todas las llamadas: `Authorization: Bearer <jwt>` (`tools/generar_token.py`).

```graphql
# Pantalla de inicio: 3 servicios, 1 peticion
{ resumen { totalActivos prestamosVigentes prestamosVencidos notificacionesNoLeidas } }

# Detalle de un activo con solo lo necesario
{ activo(placaInventario: "AF-2026-0001") {
    descripcion estado
    prestamoVigente { idPrestamo numDocumento fechaDevolucionPactada vencido }
    movimientos(tipo: PRESTAMO) { fechaMovimiento numDocumento }
} }

mutation { crearPrestamo(input: {
  placaInventario: "AF-2026-0001", numDocumento: "CC12345678",
  fechaSalida: "2026-05-01T08:00:00", fechaDevolucionPactada: "2026-05-08T08:00:00"
}) { idPrestamo estado activo { descripcion } } }

subscription { notificacionRecibida { titulo mensaje placaInventario } }
```

## Notificaciones: ¿WebSocket permanente?

No es obligatorio. Opciones, de menor a mayor costo:

1. **Query con polling** (`notificacionesNoLeidas` cada 30-60 s). Sin conexion abierta;
   suficiente si un retraso de segundos o minutos es aceptable.
2. **Subscription (implementada)**. Una conexion WebSocket por usuario conectado, util
   para avisos de prestamo vencido o activo asignado en el momento. El WebSocket se
   autentica con `connectionParams: { Authorization: "Bearer ..." }`.
3. Push nativo (FCM / Web Push) si hay que avisar con la app cerrada.

Limitacion actual: `eter-notificaciones-service` solo expone REST, asi que el gateway
consulta ese servicio cada `ETER_POLL_NOTIFICACIONES_MS` (5 s) por suscriptor y empuja
solo las nuevas. El cliente ve tiempo real, pero hay polling interno. Para eliminarlo,
el gateway deberia consumir el topic Kafka de notificaciones o ese servicio exponer
un canal de eventos. Ademas, la conexion usa el JWT del momento en que se abrio:
el cliente debe reconectar al renovar el token.
