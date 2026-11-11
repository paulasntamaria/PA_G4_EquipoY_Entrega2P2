# ETER — Diseño de tópicos y eventos Kafka (Entrega 2P2)

Este documento describe **exactamente** lo que el código publica y consume. Si cambia un campo del sobre, sube `eventVersion` y el nombre del tópico (`.v2`).

## 1. ¿Por qué Kafka en ETER?

| Necesidad real de la arquitectura | Sin Kafka (antes) | Con Kafka (ahora) |
|---|---|---|
| El custodio debe enterarse de que tiene un préstamo, que se venció o que se registró la devolución. | Préstamos tendría que llamar por REST a Notificaciones y fallar si está caída. | Préstamos publica el hecho y sigue. Notificaciones lo procesa cuando pueda. |
| El kardex de inventario debe reflejar **cada** salida/entrada del activo (préstamo, devolución, alta, baja). | Alguien tenía que registrar a mano el movimiento con `POST /movimientos-inventario`; se olvidaba y el kardex quedaba desfasado. | Inventario consume los mismos eventos y registra el movimiento automáticamente. |
| Un mismo hecho le interesa a varios servicios. | N llamadas REST acopladas. | Un evento, varios **grupos de consumidores** independientes (fan-out). |

Kafka desacopla en el tiempo (el consumidor puede estar caído), en el espacio (el productor no conoce a los consumidores) y permite reprocesar (los eventos quedan en el log).

## 2. Mapa de flujo

```text
                         ┌──────────────────────────────────────┐
 POST /prestamos ───────>│ eter-prestamos-service (:8088)       │
 PATCH /devolucion ─────>│  tx: prestamo_temporal + outbox_event│
 Vigía (cada 30-60 s) ──>│  OutboxPublisher (cada 5 s)          │
                         └───────────────┬──────────────────────┘
                                         │ key = nitUniv:placa
                                         ▼
                      ┌─────────────────────────────────────┐
                      │ eter.prestamos.eventos.v1 (3 part.) │
                      └───────┬──────────────────────┬──────┘
               grupo eter-notificaciones-v2   grupo eter-inventario-v1
                              ▼                      ▼
                ┌──────────────────────┐   ┌──────────────────────────┐
                │ notificaciones :8090 │   │ inventario :8089         │
                │ Redis + UNIQUE       │   │ Redis + UNIQUE           │
                │ → notificacion       │   │ → movimiento_inventario  │
                └──────────────────────┘   └──────────────────────────┘
                                                     ▲
                      ┌─────────────────────────────┐│ grupo eter-inventario-v1
                      │ eter.activos.eventos.v1     │┘
                      └──────────────▲──────────────┘
                                     │ key = nitUniv:placa
                         ┌───────────┴──────────────────────────┐
 POST /activos-fijos ───>│ eter-activos-service (:8087)         │
 DELETE /activos-fijos ─>│  tx: activo_fijo + outbox_event      │
                         └──────────────────────────────────────┘

 Mensajes que no se pueden procesar → <tópico>.DLT
```

## 3. Tópicos

| Tópico | Particiones | Key | Productor | Consumidores (grupo) | Retención |
|---|---:|---|---|---|---|
| `eter.prestamos.eventos.v1` | 3 | `nitUniv:placaInventario` | `eter-prestamos-service` (Outbox) | `eter-notificaciones-v2`, `eter-inventario-v1` | por defecto (7 días) |
| `eter.activos.eventos.v1` | 3 | `nitUniv:placaInventario` | `eter-activos-service` (Outbox) | `eter-inventario-v1` | por defecto |
| `eter.prestamos.eventos.v1.DLT` | 3 | la original | `DeadLetterPublishingRecoverer` de cada consumidor | revisión manual (Kafka UI) | por defecto |
| `eter.activos.eventos.v1.DLT` | 3 | la original | `DeadLetterPublishingRecoverer` de inventario | revisión manual | por defecto |

Decisiones de diseño:

- **Un tópico por agregado** (préstamo, activo), no uno por tipo de evento. Así todos los eventos de un préstamo quedan en orden en el mismo log.
- **Key = `nitUniv:placaInventario`** (identidad única del activo): todos los eventos de un activo caen en la misma partición y se consumen **en orden** (no puede llegar `PRESTAMO_DEVUELTO` antes de `PRESTAMO_CREADO`).
- **3 particiones**: permite hasta 3 instancias de cada consumidor en paralelo.
- **`auto.create.topics.enable=false`**: los tópicos solo existen si están en este diseño. Los crea el contenedor `kafka-init`.
- **Sufijo `.v1`**: si el formato cambia de forma incompatible, se crea `.v2` y conviven ambos durante la migración.
- **`acks=all`** en los productores y espera de confirmación (`send(...).get()`) antes de marcar el evento como publicado.

## 4. Sobre común (envelope)

Todos los mensajes son JSON UTF-8 con esta forma:

| Campo | Tipo | Obligatorio | Descripción |
|---|---|:-:|---|
| `eventId` | UUID | ✔ | Identificador único del evento. **Es la clave de idempotencia.** |
| `eventType` | string | ✔ | `PRESTAMO_CREADO`, `PRESTAMO_DEVUELTO`, `PRESTAMO_VENCIDO`, `ACTIVO_CREADO`, `ACTIVO_DADO_DE_BAJA`. |
| `eventVersion` | int | ✔ | Versión del esquema del evento (hoy `1`). |
| `source` | string | ✔ | Microservicio que lo emitió. |
| `assetKey` | string | ✔ | `nitUniv:placaInventario`. Igual a la key del mensaje. |
| `nitUniv` | string | ✔ | Universidad (tenant). |
| `placaInventario` | string | ✔ | Placa del activo. |
| `actor` | string | ✔ | Quién causó el hecho (documento del custodio, email del usuario o `vigia-prestamos`). |
| `occurredAt` | ISO-8601 con zona | ✔ | Momento del hecho. |
| `payload` | objeto | ✔ | Datos propios del tipo de evento (ver abajo). |

## 5. Eventos de `eter.prestamos.eventos.v1`

Payload común de préstamo:

| Campo | Tipo | Descripción |
|---|---|---|
| `tipo` | string | Igual a `eventType`. |
| `idPrestamo` | UUID | Préstamo afectado. |
| `numDocumento` | string | Documento del custodio. |
| `destinatarioEmail` | string | Correo a notificar (`{numDocumento}@eter.edu.co`). |
| `estado` | string | `VIGENTE` / `DEVUELTO`. |
| `fechaSalida` | LocalDateTime | Salida del activo. |
| `fechaDevolucionPactada` | LocalDateTime | Fecha límite. |
| `fechaDevolucionReal` | LocalDateTime \| null | Solo en `PRESTAMO_DEVUELTO`. |

| Evento | Cuándo se emite | `dedup_key` en el outbox | Notificaciones | Inventario |
|---|---|---|---|---|
| `PRESTAMO_CREADO` | `POST /api/v1/activos/prestamos` exitoso | `PRESTAMO_CREADO:{idPrestamo}` | "Préstamo registrado" | movimiento `PRESTAMO` (fecha = `fechaSalida`) |
| `PRESTAMO_DEVUELTO` | `PATCH .../prestamos/{id}/devolucion` exitoso | `PRESTAMO_DEVUELTO:{idPrestamo}` | "Préstamo devuelto" | movimiento `DEVOLUCION` (fecha = `fechaDevolucionReal`) |
| `PRESTAMO_VENCIDO` | El vigía encuentra un préstamo VIGENTE con fecha pactada vencida | `PRESTAMO_VENCIDO:{idPrestamo}` (una sola vez por préstamo) | "Préstamo vencido" | se ignora (no mueve inventario) |

Ejemplo real (`PRESTAMO_CREADO`):

```json
{
  "eventId": "3f0f6a3e-6c55-4a8e-9a57-2d0f6f1d6f10",
  "eventType": "PRESTAMO_CREADO",
  "eventVersion": 1,
  "source": "eter-prestamos-service",
  "assetKey": "900123456-1:AF-2026-0002",
  "nitUniv": "900123456-1",
  "placaInventario": "AF-2026-0002",
  "actor": "79123456",
  "occurredAt": "2026-10-03T20:15:02.311-05:00",
  "payload": {
    "tipo": "PRESTAMO_CREADO",
    "idPrestamo": "a8d1c7b2-1e4f-4c55-9a10-6b7e2f0d9c33",
    "nitUniv": "900123456-1",
    "placaInventario": "AF-2026-0002",
    "numDocumento": "79123456",
    "destinatarioEmail": "79123456@eter.edu.co",
    "assetKey": "900123456-1:AF-2026-0002",
    "estado": "VIGENTE",
    "fechaSalida": "2026-10-03T08:00",
    "fechaDevolucionPactada": "2026-10-10T18:00",
    "fechaDevolucionReal": null
  }
}
```

## 6. Eventos de `eter.activos.eventos.v1`

| Campo del payload | Tipo | Descripción |
|---|---|---|
| `tipo` | string | Igual a `eventType`. |
| `nitUniv`, `placaInventario`, `assetKey` | string | Identidad del activo. |
| `descripcion`, `codUnspsc`, `codSede` | string | Datos del activo. |
| `valorContable` | número | Valor contable. |
| `estado` | string | `ACTIVO`, `MANTENIMIENTO` o `BAJA`. |
| `numDocumentoResponsable` | string | Documento del usuario que hizo la operación (parte local del email del JWT). |

| Evento | Cuándo se emite | Inventario |
|---|---|---|
| `ACTIVO_CREADO` | `POST /api/v1/activos/activos-fijos` exitoso | movimiento `ALTA` (fecha = `occurredAt`) |
| `ACTIVO_DADO_DE_BAJA` | `DELETE /api/v1/activos/activos-fijos/{nit}/{placa}` cuando el activo **no** estaba ya en BAJA | movimiento `BAJA` |

## 7. Garantías de entrega

### Productor: Transactional Outbox
1. El caso de uso guarda el cambio de negocio **y** la fila `outbox_event` en la **misma transacción**.
2. `OutboxPublisher` (cada 5 s) envía los pendientes con `acks=all` y espera la confirmación.
3. Solo entonces marca `published_at`. Si Kafka falla, guarda `last_error`, suma `publish_attempts` y lo reintenta en la siguiente vuelta. **Un fallo no detiene al resto del lote** (corregido en esta entrega).

Resultado: *at-least-once*. Nunca se pierde un evento, pero puede publicarse dos veces (por ejemplo, si el servicio muere entre el `send` y el `UPDATE published_at`). Por eso los consumidores **deben** ser idempotentes.

### Consumidores: idempotencia en dos niveles

```text
llega evento ─► ¿JSON válido y con eventId UUID? ── no ──► MensajeKafkaInvalidoException ─► .DLT
                     │ sí
                     ▼
     Redis: SET eter:idem:{grupo}:{eventId} PROCESANDO NX EX 300
          │ OK                         │ ya existía
          ▼                            ▼
   procesar en PostgreSQL       GET clave ─► PROCESADO  ─► log "DUPLICADO", se confirma offset
   (UNIQUE como 2.ª barrera)              └► PROCESANDO ─► EventoEnProcesoException ─► reintento
          │ ok            │ error
          ▼               ▼
   SET clave PROCESADO   DEL clave ─► reintento con backoff 1s,2s,4s,8s,10s,10s ─► .DLT
   EX 7 días
```

| Nivel | Dónde | Qué protege |
|---|---|---|
| 1. Redis | `RedisIdempotenciaEventos` (`shared/idempotencia`) en notificaciones e inventario | Descarta el duplicado **sin tocar la base**. La clave incluye el **grupo** porque cada consumidor debe procesar el evento una vez (`eter:idem:eter-inventario-v1:<id>` y `eter:idem:eter-notificaciones-v2:<id>` son independientes). |
| 2. PostgreSQL | `notificacion`: `UNIQUE(event_id, destinatario_email)`. `movimiento_inventario`: índice único `(nit_univ, idempotency_key)` con `idempotency_key = 'evt:' + eventId` | Respaldo si Redis se reinició, la clave expiró o Redis no está disponible. |

**Redis caído**: el consumidor registra `WARN Redis no disponible…`, procesa igual (*fail-open*) y la restricción UNIQUE evita el duplicado. El servicio **no** se reporta DOWN por Redis (`management.health.redis.enabled=false`).

### Manejo de errores (`KafkaErrorConfig`)

| Tipo de error | Ejemplos | Acción |
|---|---|---|
| No recuperable | JSON roto, falta `eventId`, `eventId` no UUID, fecha inválida, `DatoInvalidoException` | Directo a `<tópico>.DLT` sin reintentos. Log `ERROR … enviado al DLT`. |
| Transitorio | PostgreSQL caído, `EventoEnProcesoException` | Reintento con backoff exponencial (6 intentos). Log `WARN … Se reintentará`. Si se agotan, va al `.DLT`. |

Los mensajes del DLT conservan la key y llevan cabeceras `kafka_dlt-exception-message`, `kafka_dlt-original-topic`, `kafka_dlt-original-offset`, visibles en Kafka UI.

## 8. Logs que deja cada paso

| Servicio | Mensaje |
|---|---|
| prestamos/activos | `Evento <id> (PRESTAMO_CREADO) publicado en eter.prestamos.eventos.v1 con key 900123456-1:AF-…` |
| consumidores | `Evento recibido <id> [PRESTAMO_CREADO] topico=… particion=… offset=… key=…` |
| consumidores | `Evento <id> procesado (PROCESADO). …` |
| consumidores | `Evento <id> DUPLICADO: ya figura como procesado en Redis (eter:idem:…). Se ignora.` |
| consumidores | `Evento <id> [PRESTAMO_VENCIDO] no genera movimiento de inventario. Se ignora.` |
| consumidores | `Mensaje offset … enviado al DLT: …` |
