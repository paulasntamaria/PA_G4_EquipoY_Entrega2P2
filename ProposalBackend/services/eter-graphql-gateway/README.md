# ETER GraphQL Gateway

Punto de entrada único del frontend. Spring Boot 4 (Java 21) con **Spring for GraphQL**: traduce
cada operación GraphQL a las APIs REST de los 4 microservicios (activos 8087, préstamos 8088,
inventario 8089 y notificaciones 8090).

- **Query**: el frontend pide solo los campos que necesita. Los campos que viven en otro servicio
  (`ActivoFijo.prestamos`, `Prestamo.activo`…) solo se consultan si aparecen en la query, y las
  lecturas de activos se memoizan por petición (sin N+1).
- **En paralelo**: con hilos virtuales activados, Spring for GraphQL resuelve los campos de una
  misma consulta a la vez (por ejemplo `prestamoVigente` y `movimientos` de un activo). Por eso la
  memoización guarda la consulta *en curso* (`CompletableFuture`): si dos campos piden el mismo
  activo al mismo tiempo, el segundo espera al primero en vez de repetir la llamada.
- **Mutation**: una por cada POST/PUT/PATCH/DELETE de los microservicios.
- **Subscription**: `notificacionRecibida` por WebSocket en la misma ruta `/graphql`, con el
  protocolo `graphql-transport-ws`.
- **Seguridad**: el gateway valida el JWT (HS256, mismo secreto que los microservicios) y lo
  reenvía. Cada microservicio conserva sus reglas de rol (ADMIN para escribir) y de universidad.
  El cliente nunca manda el `nitUniv`: sale del token.

## Ejecutar

```cmd
gradlew.bat bootRun          :: http://localhost:4000/graphql (ws: misma ruta)
gradlew.bat test             :: 12 pruebas
```

O con todo el ecosistema: `docker compose up -d --build` en `infrastructure` (servicio
`graphql-gateway`). Salud: `http://localhost:4000/actuator/health`.

Variables: `PORT`, `ETER_JWT_SECRET`, `ETER_{ACTIVOS,PRESTAMOS,INVENTARIO,NOTIFICACIONES}_BASE_URL`,
`ETER_DOWNSTREAM_TIMEOUT_MS` (5000) y `ETER_POLL_NOTIFICACIONES_MS` (5000).

## Estructura

Misma organización que los microservicios: los controladores GraphQL son adaptadores de entrada y
los clientes REST, adaptadores de salida.

```text
src/main/resources/graphql/schema.graphqls      el contrato: tipos, queries, mutations y subscription
src/main/java/com/eter/eterbackend/
├── gateway/infrastructure/
│   ├── adapter/in/graphql/      *GraphqlController: @QueryMapping, @MutationMapping,
│   │                            @SubscriptionMapping y @SchemaMapping (campos entre servicios)
│   ├── adapter/out/rest/        MicroservicioRestClient (RestClient + JWT + timeout) y un
│   │                            cliente por servicio: ActivosClient, PrestamosClient, ...
│   └── config/GraphqlConfig     escalar DateTime
└── shared/
    ├── security/                JwtVerificador, AutenticacionInterceptor (HTTP y WebSocket),
    │                            ContextoPeticion (usuario del token + caché de la petición)
    └── error/                   GatewayException y los resolvers que la convierten en error GraphQL
```

El JSON de los microservicios se pasa como mapas y listas: GraphQL lee los campos por nombre, así
que no hacen falta DTO y el contrato lo define solo `schema.graphqls`.

## Errores

GraphQL responde HTTP 200 e informa cada fallo en `errors[].extensions`:

| Respuesta del microservicio | `extensions.code` |
|---|---|
| Sin token o token inválido (lo detecta el gateway) | `UNAUTHENTICATED` |
| 400 / 422 | `BAD_USER_INPUT` (con `errores` por campo si el servicio los envía) |
| 401 | `UNAUTHENTICATED` |
| 403 | `FORBIDDEN` |
| 404 | `NOT_FOUND` (en `activo(placa)` se devuelve `null`) |
| 409 | `CONFLICT` |
| 5xx, caído o sin respuesta a tiempo | `SERVICE_UNAVAILABLE` |

Cada error lleva además `servicio` (y `status` si hubo respuesta). Si falla un campo, los demás
se entregan igual (datos parciales).

## Ejemplos

Cabecera en todas las llamadas: `Authorization: Bearer <jwt>` (`tools/generar_token.py`).

```graphql
# Pantalla de inicio: 3 servicios, 1 petición (en paralelo)
{ resumen { totalActivos prestamosVigentes prestamosVencidos notificacionesNoLeidas } }

# Detalle de un activo con solo lo necesario
{ activo(placaInventario: "AF-2026-0001") {
    descripcion estado
    prestamoVigente { idPrestamo numDocumento fechaDevolucionPactada vencido }
    movimientos(tipo: PRESTAMO) { fechaMovimiento numDocumento }
} }

mutation { crearPrestamo(input: {
  placaInventario: "AF-2026-0001", numDocumento: "79123456",
  fechaSalida: "2026-05-01T08:00:00", fechaDevolucionPactada: "2026-05-08T08:00:00"
}) { idPrestamo estado activo { descripcion } } }

subscription { notificacionRecibida { titulo mensaje placaInventario } }
```

## Notificaciones en vivo

El WebSocket se autentica en el mensaje `connection_init`, con
`payload: { Authorization: "Bearer ..." }`. Como `eter-notificaciones-service` solo expone REST,
el gateway consulta la bandeja de cada suscriptor cada `ETER_POLL_NOTIFICACIONES_MS` y empuja solo
las notificaciones nuevas (la primera consulta fija la línea base). El cliente ve tiempo real,
pero por dentro hay sondeo. Para eliminarlo, el gateway podría consumir los eventos de Kafka
directamente. La conexión usa el JWT con el que se abrió: al renovar el token, el cliente debe
reconectar.

## Pruebas

`GatewayGraphqlTest` levanta el gateway real en un puerto aleatorio contra microservicios
simulados (servidor HTTP del JDK) y verifica: sin token o con firma falsa → `UNAUTHENTICATED`;
`me` sale del token; solo se consultan los servicios de los campos pedidos; composición sin N+1;
5 préstamos de 3 activos hacen 3 llamadas a activos aunque los campos se resuelvan en paralelo;
el resumen agrega 3 servicios; 404 → `null`; 409 → `CONFLICT` con su servicio; `DateTime`
inválido rechazado sin llamar al backend; CORS para la página abierta desde `file://`; y la
suscripción por WebSocket emite solo las notificaciones nuevas.
