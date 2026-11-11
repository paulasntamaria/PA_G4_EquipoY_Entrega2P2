# ETER — Cómo ver y explicar cada una de las 11 evidencias (Entrega 2P2)

Este documento complementa `GUIA_ECOSISTEMA_4_MICROSERVICIOS.md` y `docs/EVENTOS_KAFKA.md`. Para cada evidencia: **qué es**, **cómo verla**, **qué decir** y **qué señalar en el pantallazo**.

## 0. La idea central (explíquela primero, en 30 segundos)

> "ETER tiene 4 microservicios. Cuando pasa algo importante (se crea un activo, se presta, se devuelve, se vence un préstamo), el servicio dueño **publica un evento en Kafka**. Otros servicios lo **consumen** y hacen su trabajo: Inventario anota el movimiento en el kardex y Notificaciones avisa al custodio. Nadie llama a nadie por REST, y si un consumidor está caído, procesa los eventos al volver."

Piezas para tener claras:

| Concepto | En ETER | Por qué importa |
|---|---|---|
| Productor | `activos` (:8087) y `prestamos` (:8088) | Publican eventos. |
| Consumidor | `inventario` (:8089) y `notificaciones` (:8090) | Reaccionan a los eventos. |
| Tópico | `eter.activos.eventos.v1`, `eter.prestamos.eventos.v1` | Canal donde viajan los eventos (3 particiones). |
| Outbox | Tabla `outbox_event` | El cambio de negocio y el evento se guardan en **la misma transacción**; un publicador los envía a Kafka cada 5 s. Nunca se pierde un evento. |
| Grupo de consumidores | `eter-inventario-v1`, `eter-notificaciones-v2` | Cada grupo recibe **todos** los eventos (fan-out). |
| Idempotencia | Redis (`eter:idem:{grupo}:{eventId}`) + `UNIQUE` en PostgreSQL | Kafka puede entregar un evento 2 veces; el resultado no debe duplicarse. |
| DLT | `<tópico>.DLT` | Buzón de mensajes que no se pueden procesar. |

Flujo corto que recorre la demo:

```text
POST activo   -> ACTIVO_CREADO     -> Inventario: ALTA
POST préstamo -> PRESTAMO_CREADO   -> Inventario: PRESTAMO  + Notificaciones: "registrado"
Vigía (auto)  -> PRESTAMO_VENCIDO  -> Notificaciones: "vencido"  (Inventario lo ignora)
PATCH devol.  -> PRESTAMO_DEVUELTO -> Inventario: DEVOLUCION + Notificaciones: "devuelto"
```

## Cómo generarlas todas

```cmd
cd infrastructure
docker compose up -d --build
cd ..
powershell -ExecutionPolicy Bypass -File tools\demo_kafka.ps1
```

El script imprime un bloque **"EVIDENCIA N"** por cada una (dura ~1,5 min porque espera al vigía). Un pantallazo por bloque, con PowerShell maximizado. Para ver lo mismo en el navegador: **Kafka UI → http://localhost:8092**.

---

## Evidencia 1 — `docker compose ps` con los 8 contenedores
**Criterio:** Docker Compose.

- **Qué es:** el entorno completo se levanta con un solo comando.
- **Cómo verla:** `cd infrastructure` → `docker compose ps`.
- **Qué debe aparecer (8):** `eter-activos`, `eter-prestamos`, `eter-inventario`, `eter-notificaciones`, `eter-postgres`, `eter-kafka`, `eter-redis` (todos `Up (healthy)`) y `eter-kafka-ui` (`Up`). Además `eter-kafka-init` como `Exited (0)`: solo crea los tópicos y termina (es normal).
- **Cómo explicarla:** "Con `docker compose up -d --build` se compilan y arrancan los 4 microservicios y 4 piezas de infraestructura (PostgreSQL, Kafka, Redis, Kafka UI). El estado `healthy` indica que cada servicio pasó su healthcheck, no solo que arrancó."
- **Señalar:** la columna STATUS en `healthy` y los puertos 8087–8090, 9092, 6379, 5433, 8092.

## Evidencia 2 — Tópicos y particiones
**Criterio:** Diseño de tópicos.

- **Qué es:** los canales de Kafka que diseñamos.
- **Cómo verla:** en la demo (bloque 2) o `docker exec eter-kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:19092 --list`. En Kafka UI: pestaña *Topics*.
- **Qué debe aparecer:** `eter.prestamos.eventos.v1` y `eter.activos.eventos.v1` con `PartitionCount: 3`, más sus `.DLT`.
- **Cómo explicarla:**
  - Un tópico por **agregado** (préstamo, activo), no por tipo de evento: así los eventos de un mismo préstamo quedan en orden.
  - La **key** es `nitUniv:placaInventario`: todos los eventos de un activo caen en la misma partición y se consumen en orden (no llega `DEVUELTO` antes de `CREADO`).
  - **3 particiones** permiten hasta 3 instancias de cada consumidor en paralelo.
  - Sufijo `.v1` para versionar; `auto.create.topics.enable=false`, así que solo existen los tópicos diseñados.
- **Señalar:** `PartitionCount: 3` y los tópicos `.DLT`.

## Evidencia 3 — Alta de activo → `ACTIVO_CREADO` → movimiento ALTA
**Criterio:** Productor/consumidor, integración.

- **Qué es:** el ciclo completo productor → Kafka → consumidor en su forma más simple.
- **Cómo verla:** demo (bloque 3), o Postman: *Crear activo* y luego *Kardex del activo (ALTA)* tras ~5 s.
- **Qué pasa por dentro:**
  1. `POST :8087/.../activos-fijos` guarda el activo y la fila `outbox_event` en una sola transacción.
  2. `OutboxPublisher` (cada 5 s) publica `ACTIVO_CREADO` en `eter.activos.eventos.v1`.
  3. Inventario lo consume y crea un movimiento `ALTA` en su kardex.
- **Cómo explicarla:** "Yo solo creé el activo en Activos. Nadie escribió el movimiento en Inventario: lo hizo el consumidor al recibir el evento."
- **Señalar:** el log `Evento … (ACTIVO_CREADO) publicado` de `eter-activos`, el log `ALTA del activo …` de `eter-inventario`, y el kardex con una fila `ALTA`.

## Evidencia 4 — Préstamo → `PRESTAMO_CREADO` → movimiento PRESTAMO y notificación
**Criterio:** Productores y consumidores, fan-out (2 grupos).

- **Qué es:** un solo evento atendido por **dos servicios distintos**.
- **Cómo verla:** demo (bloque 4); Postman: *Crear préstamo*, luego *Kardex* y *Mi bandeja*.
- **Cómo explicarla:** "Un único `PRESTAMO_CREADO` en `eter.prestamos.eventos.v1` lo reciben dos grupos de consumidores independientes: `eter-inventario-v1` registra el movimiento `PRESTAMO` y `eter-notificaciones-v2` crea la notificación 'Préstamo registrado'. Cada grupo lleva su propio offset, por eso uno no afecta al otro. Eso es el fan-out."
- **Señalar:** el kardex con `ALTA` + `PRESTAMO`, la bandeja con `PRESTAMO_CREADO`, y los logs de ambos consumidores con el **mismo eventId**.
- **Detalle:** el préstamo se crea ya vencido (salida hace 3 días, pactada ayer) para que la evidencia 5 ocurra sin esperar.

## Evidencia 5 — Vigía → `PRESTAMO_VENCIDO`
**Criterio:** Procesamiento.

- **Qué es:** un evento que **no nace de una petición del usuario**, sino de un proceso automático.
- **Cómo verla:** demo (bloque 5, espera hasta 90 s); o en Postman esperar ~30 s y consultar *Mi bandeja*.
- **Cómo explicarla:** "El vigía de préstamos corre cada 30 s, busca préstamos `VIGENTE` con fecha pactada vencida y publica `PRESTAMO_VENCIDO` una sola vez por préstamo (`dedup_key = PRESTAMO_VENCIDO:{idPrestamo}`). Notificaciones avisa; Inventario **lo ignora a propósito** porque un vencimiento no mueve el activo."
- **Señalar:** la notificación 'Préstamo vencido' y el log de inventario `[PRESTAMO_VENCIDO] no genera movimiento… Se ignora`. Es la prueba de que cada consumidor decide qué eventos le importan.

## Evidencia 6 — Devolución → DEVOLUCION + notificación; kardex completo
**Criterio:** Persistencia / resultado verificable.

- **Qué es:** cierre del ciclo de vida y prueba del resultado final.
- **Cómo verla:** demo (bloque 6); Postman: *Registrar devolución* (`200`; repetirla da `409`) y *Kardex (DEVOLUCION)*.
- **Cómo explicarla:** "`PATCH …/devolucion` emite `PRESTAMO_DEVUELTO`. Inventario añade `DEVOLUCION` y Notificaciones avisa 'Préstamo devuelto'. El kardex final muestra **ALTA → PRESTAMO → DEVOLUCION**, todo generado por eventos, sin digitación manual."
- **Señalar:** las 3 filas del kardex en orden y las 3 notificaciones (registrado, vencido, devuelto). Los datos quedan en PostgreSQL, así que se pueden verificar con SQL (sección 9 de la guía).

## Evidencia 7 — Outbox publicado y claves en Redis con TTL
**Criterio:** Idempotencia (Redis).

- **Qué es:** las dos huellas que dejan las garantías de entrega.
- **Cómo verla:** demo (bloque 7), o a mano:
  ```cmd
  docker exec -it eter-postgres psql -U postgres -d eterdb -c "SELECT event_type, published_at, publish_attempts FROM prestamos.outbox_event ORDER BY occurred_at DESC LIMIT 5;"
  docker exec -it eter-redis redis-cli --scan --pattern "eter:idem:*"
  docker exec -it eter-redis redis-cli TTL eter:idem:eter-inventario-v1:<eventId>
  ```
- **Cómo explicarla:**
  - **Outbox:** `published_at` lleno = el evento ya llegó a Kafka con `acks=all`. Si Kafka falla se guarda `last_error` y se reintenta. Garantía *at-least-once*: no se pierde, pero puede duplicarse.
  - **Redis:** por eso los consumidores marcan cada evento con `SET eter:idem:{grupo}:{eventId} … NX`. Primero `PROCESANDO` (TTL 300 s) y al terminar `PROCESADO` (TTL 7 días).
- **Señalar:** `publicado = t` y `publish_attempts = 1`; claves con valor `PROCESADO` y `TTL ≈ 604800 s`. Note que hay **una clave por grupo** para el mismo evento (inventario y notificaciones), porque cada consumidor debe procesarlo una vez.

## Evidencia 8 — Simulador re-publica el mismo evento → `DUPLICADO`
**Criterio:** Idempotencia.

- **Qué es:** prueba de que un duplicado **no** cambia el resultado.
- **Cómo verla:** demo (bloque 8) o manual:
  ```cmd
  powershell -ExecutionPolicy Bypass -File tools\simular_evento.ps1 -Tipo duplicado -Buscar PRESTAMO_CREADO
  ```
- **Cómo explicarla:** "El simulador vuelve a publicar un `PRESTAMO_CREADO` con el **mismo eventId**. Los consumidores consultan Redis, ven `PROCESADO` y lo descartan sin tocar la base. Solo queda el log `DUPLICADO` y se confirma el offset."
- **Señalar:** el log `Evento <id> DUPLICADO: ya figura como procesado en Redis… Se ignora` en ambos consumidores y la línea amarilla **"Movimientos antes=N después=N | Notificaciones antes=M después=M"** con los mismos valores. Esa línea es la prueba.

## Evidencia 9 — Redis apagado → `WARN` y PostgreSQL frena el duplicado
**Criterio:** Manejo de errores.

- **Qué es:** la segunda barrera de idempotencia, cuando falla la primera.
- **Cómo verla:** demo (bloque 9; hace `docker stop eter-redis`, republica y luego `docker start eter-redis`). Se omite con `-SinRedisCaido`.
- **Cómo explicarla:** "Con Redis caído el consumidor registra `WARN Redis no disponible…` y procesa igual (*fail-open*): prefiere no detener el sistema. El duplicado lo frena PostgreSQL: `movimiento_inventario` tiene índice único `(nit_univ, idempotency_key)` con `evt:<eventId>`, y `notificacion` tiene `UNIQUE(event_id, destinatario_email)`. Redis es una optimización; la base es la garantía final. Además el servicio no se reporta DOWN por Redis."
- **Señalar:** el `WARN` en los logs, y los conteos **sin cambio** (`Movimientos=N Notificaciones=M (sin cambios)`).

## Evidencia 10 — Mensaje inválido → `.DLT` con la causa
**Criterio:** Errores y DLT.

- **Qué es:** qué pasa con un mensaje que nunca podrá procesarse.
- **Cómo verla:** demo (bloque 10) o `tools\simular_evento.ps1 -Tipo invalido`. En Kafka UI: tópico `eter.prestamos.eventos.v1.DLT` → *Messages* → *Headers*.
- **Cómo explicarla:** "Se distinguen dos tipos de error. **No recuperables** (JSON roto, sin `eventId`, `eventId` que no es UUID, fecha inválida) van directo a `<tópico>.DLT` **sin reintentos**, porque reintentar no los arregla. **Transitorios** (PostgreSQL caído) se reintentan 6 veces con backoff creciente (1, 2, 4, 8, 10, 10 s) y si se agotan también van al DLT. Así un mensaje malo no bloquea la partición."
- **Señalar:** el log `Mensaje offset … enviado al DLT`, y en el DLT la key original, el valor y la cabecera `kafka_dlt-exception-message` (la **causa**). Hay una copia por grupo consumidor.

## Evidencia 11 — Grupos de consumidores con LAG 0
**Criterio:** Consumidores funcionales.

- **Qué es:** confirmación de que los consumidores están vivos y al día.
- **Cómo verla:** demo (bloque 11), o Kafka UI → *Consumers*, o:
  ```cmd
  docker exec eter-kafka /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:19092 --describe --group eter-inventario-v1
  ```
- **Cómo explicarla:** "LAG = último offset del tópico − offset confirmado por el grupo. LAG 0 significa que no queda ningún mensaje pendiente: todo se procesó o se envió al DLT. Aparecen los dos grupos, `eter-inventario-v1` (lee ambos tópicos) y `eter-notificaciones-v2` (solo préstamos)."
- **Señalar:** la columna `LAG` en 0 en todas las particiones y los dos grupos presentes.

---

## Resumen para exponer (una línea por evidencia)

| # | Frase para decir |
|---|---|
| 1 | Todo el ecosistema se levanta con un comando y está saludable. |
| 2 | Dos tópicos de 3 particiones, con key por activo para conservar el orden. |
| 3 | Crear un activo genera solo su movimiento ALTA. |
| 4 | Un evento, dos consumidores independientes (fan-out). |
| 5 | Un proceso automático también produce eventos. |
| 6 | El kardex completo se construyó solo con eventos. |
| 7 | Outbox garantiza publicar; Redis deja la huella de lo procesado. |
| 8 | Reenviar un evento no duplica nada. |
| 9 | Si Redis cae, PostgreSQL sigue garantizando la idempotencia. |
| 10 | Lo imposible de procesar va al DLT con su causa, sin bloquear. |
| 11 | Los consumidores están al día (LAG 0). |

## Preguntas que pueden hacerle (y la respuesta corta)

- **¿Por qué Outbox y no publicar directo a Kafka?** Porque guardar en BD y publicar son dos operaciones; si una falla, quedan inconsistentes. Con Outbox el evento se guarda en la misma transacción y se publica después.
- **¿Se puede perder un evento?** No (`acks=all` + reintento del Outbox). Sí puede duplicarse, y por eso hay idempotencia.
- **¿Por qué dos niveles de idempotencia?** Redis es rápido y evita tocar la base; PostgreSQL es el respaldo si Redis se reinicia, expira o cae.
- **¿Por qué la key es `nitUniv:placa`?** Mismo activo → misma partición → orden garantizado.
- **¿Por qué Inventario ignora `PRESTAMO_VENCIDO`?** Porque el activo sigue prestado: no hay movimiento físico.
- **¿Qué pasa si un consumidor está caído?** Los eventos quedan en el log de Kafka y los procesa al volver.
