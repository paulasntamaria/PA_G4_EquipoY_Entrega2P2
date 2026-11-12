# ETER — Guía de ejecución del ecosistema (Entrega 2P2: Kafka e integración orientada a eventos)

> **Resumen rápido**
> ```cmd
> cd infrastructure
> docker compose up -d --build
> cd ..
> powershell -ExecutionPolicy Bypass -File tools\demo_kafka.ps1
> ```
> El primer comando levanta **todo** (PostgreSQL, Kafka, Redis, Kafka UI y los 4 microservicios). El segundo recorre el flujo completo e imprime las 11 evidencias para el PDF.

## 1. Arquitectura

| Microservicio | Puerto | Responsabilidad | Kafka | Esquema PostgreSQL |
|---|---:|---|---|---|
| `eter-activos-service` | 8087 | CRUD de activos fijos | **Productor** (`eter.activos.eventos.v1`) | `activos` |
| `eter-prestamos-service` | 8088 | Préstamos, devoluciones, vigía de vencidos | **Productor** (`eter.prestamos.eventos.v1`) | `prestamos` |
| `eter-inventario-service` | 8089 | Asignaciones y kardex de movimientos | **Consumidor** de ambos tópicos (grupo `eter-inventario-v1`) | `inventario` |
| `eter-notificaciones-service` | 8090 | Bandeja de notificaciones del usuario | **Consumidor** de préstamos (grupo `eter-notificaciones-v2`) | `notificaciones` |
| `eter-graphql-gateway` | 4000 | API Gateway GraphQL (Spring Boot + Spring for GraphQL): único punto de entrada del frontend; traduce GraphQL a las APIs REST de los 4 servicios | — | — (sin base de datos) |

| Infraestructura | Puerto local | Para qué |
|---|---:|---|
| PostgreSQL 16 | 5433 | Un contenedor, un *schema* por microservicio (cada uno con sus migraciones Flyway). |
| Kafka 3.9 (KRaft) | 9092 | Bus de eventos. Los tópicos los crea `kafka-init`. |
| Redis 7.4 | 6379 | Registro de eventos ya procesados (idempotencia de los consumidores). |
| Kafka UI | 8092 | Ver tópicos, mensajes, DLT y grupos de consumidores en el navegador. |

```text
                    REST (valida placa)
  :8088 Préstamos ─────────────────────────────> :8087 Activos
   │ Outbox                                          │ Outbox
   ▼                                                 ▼
 eter.prestamos.eventos.v1                    eter.activos.eventos.v1
   │                     │                           │
   │ eter-notificaciones-v2   eter-inventario-v1 ◄───┘
   ▼                     ▼
 :8090 Notificaciones   :8089 Inventario  ──REST──> :8087 (solo en POST manuales)
   │  Redis + UNIQUE       │  Redis + UNIQUE
   ▼                       ▼
 notificacion          movimiento_inventario
```

**¿Por qué Kafka?** Con un mismo hecho (se prestó, se devolvió, se dio de alta o de baja un activo) **dos servicios** hacen trabajo distinto: notificaciones avisa al custodio e inventario registra el movimiento en el kardex. Kafka evita que préstamos y activos llamen por REST a cada interesado y garantiza que, si un consumidor está caído, procese los eventos al volver. El detalle de tópicos, eventos y garantías está en **[`docs/EVENTOS_KAFKA.md`](docs/EVENTOS_KAFKA.md)**.

### Endpoints

| Grupo | Base URL | Endpoints |
|---|---|---|
| Activos fijos | `http://localhost:8087` | `/api/v1/activos/activos-fijos...` |
| Préstamos | `http://localhost:8088` | `/api/v1/activos/prestamos...` |
| Asignaciones / movimientos | `http://localhost:8089` | `/api/v1/activos/asignaciones...`, `/api/v1/activos/movimientos-inventario...` |
| Notificaciones | `http://localhost:8090` | `/api/v1/notificaciones...` |

## 2. Requisitos

- **Docker Desktop** encendido (con unos 4 GB de RAM libres).
- **Python 3** (solo para generar el JWT de pruebas con `tools\generar_token.py`).
- Java 21 **no** es necesario para la opción A: las imágenes se compilan dentro de Docker.

## 3. Opción A (recomendada para la entrega): todo con Docker Compose

Desde `infrastructure`:

```cmd
docker compose up -d --build
docker compose ps
```

La primera vez tarda varios minutos porque compila los 4 servicios dentro de Docker. Las siguientes veces basta con `docker compose up -d`.

Debe ver 9 contenedores `Up`. Los 4 microservicios, el gateway, postgres, kafka y redis aparecen como `(healthy)`, y `eter-kafka-init` como `Exited (0)` porque solo crea los tópicos y termina. Los servicios tardan entre 30 y 60 s en pasar a `healthy`; el gateway arranca cuando los 4 microservicios ya lo están.

```text
NAME                   IMAGE                                    STATUS
eter-graphql-gateway   eter/graphql-gateway:entrega2p2          Up (healthy)
eter-activos          eter/activos-service:entrega2p2          Up (healthy)
eter-prestamos        eter/prestamos-service:entrega2p2        Up (healthy)
eter-inventario       eter/inventario-service:entrega2p2       Up (healthy)
eter-notificaciones   eter/notificaciones-service:entrega2p2   Up (healthy)
eter-postgres         postgres:16-alpine                       Up (healthy)
eter-kafka            apache/kafka:3.9.0                       Up (healthy)
eter-redis            redis:7.4-alpine                         Up (healthy)
eter-kafka-ui         provectuslabs/kafka-ui:latest            Up
```

Logs de un servicio (útil para los pantallazos):

```cmd
docker compose logs -f inventario
docker compose logs -f notificaciones
```

Si cambia código Java, reconstruya solo ese servicio: `docker compose up -d --build inventario`.

## 4. Opción B (desarrollo): infraestructura en Docker y servicios desde el IDE

```cmd
cd infrastructure
docker compose up -d postgres kafka kafka-init redis kafka-ui
```

Luego abra **cinco terminales**, una por servicio (el gateway al final):

```cmd
cd services\eter-activos-service        &  gradlew.bat bootRun
cd services\eter-prestamos-service      &  gradlew.bat bootRun
cd services\eter-inventario-service     &  gradlew.bat bootRun
cd services\eter-notificaciones-service &  gradlew.bat bootRun
cd services\eter-graphql-gateway        &  gradlew.bat bootRun
```

Los valores por defecto de cada `application.yml` ya apuntan a `localhost` (5433, 9092 y 6379). **No mezcle las dos opciones**: si los contenedores de los servicios están arriba, los puertos 8087 a 8090 ya están ocupados. Para pasar de A a B, ejecute `docker compose stop activos prestamos inventario notificaciones`.

## 5. Autenticación

Los cuatro servicios validan el mismo JWT. Genere un token de desarrollo desde `ProposalBackend`:

```cmd
python tools\generar_token.py
```

En Postman, pegue el valor en la variable `token` de la colección, sin anteponer `Bearer`. El token es del usuario `79123456@eter.edu.co`, universidad `900123456-1`, rol `ADMIN`.

Swagger (no requiere token para abrirse): `http://localhost:8087/swagger-ui.html` (y lo mismo en 8088, 8089 y 8090).

## 6. Flujo Kafka implementado

| # | Acción del usuario | Evento | Tópico | Qué hace cada consumidor |
|---|---|---|---|---|
| 1 | `POST :8087/.../activos-fijos` | `ACTIVO_CREADO` | `eter.activos.eventos.v1` | Inventario → movimiento **ALTA** |
| 2 | `POST :8088/.../prestamos` | `PRESTAMO_CREADO` | `eter.prestamos.eventos.v1` | Inventario → **PRESTAMO**; Notificaciones → "Préstamo registrado" |
| 3 | (automático) vigía de préstamos | `PRESTAMO_VENCIDO` | `eter.prestamos.eventos.v1` | Notificaciones → "Préstamo vencido"; Inventario lo ignora |
| 4 | `PATCH :8088/.../prestamos/{id}/devolucion` | `PRESTAMO_DEVUELTO` | `eter.prestamos.eventos.v1` | Inventario → **DEVOLUCION**; Notificaciones → "Préstamo devuelto" |
| 5 | `DELETE :8087/.../activos-fijos/{nit}/{placa}` | `ACTIVO_DADO_DE_BAJA` | `eter.activos.eventos.v1` | Inventario → **BAJA** |

Paso a paso dentro del sistema:

1. El caso de uso guarda el cambio **y** la fila en `outbox_event` en la misma transacción (patrón *Transactional Outbox*).
2. `OutboxPublisher` publica los pendientes cada 5 s con key `nitUniv:placaInventario` y `acks=all`.
3. Cada consumidor valida el mensaje y consulta Redis (`eter:idem:{grupo}:{eventId}`):
   - si el evento ya está marcado `PROCESADO`, lo descarta como **duplicado**;
   - si no, lo procesa en PostgreSQL y lo marca `PROCESADO` (TTL de 7 días).
4. Un error transitorio se reintenta 6 veces con espera creciente. Un mensaje inválido va directo a `<tópico>.DLT`.

Ver los mensajes directamente:

```cmd
docker exec -it eter-kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:19092 --topic eter.prestamos.eventos.v1 --from-beginning --property print.key=true
docker exec -it eter-kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:19092 --topic eter.activos.eventos.v1 --from-beginning --property print.key=true
```

O en el navegador: **Kafka UI → `http://localhost:8092`** → *Topics* → elegir el tópico → *Messages*. En *Consumers* se ven los dos grupos y su LAG.

## 7. Demostración automática (genera las evidencias del PDF)

Con todo arriba, desde `ProposalBackend`:

```cmd
powershell -ExecutionPolicy Bypass -File tools\demo_kafka.ps1
```

El script usa una placa nueva en cada corrida (`AF-DEMO-MMddHHmmss`), así que se puede repetir. Tarda cerca de 1,5 minutos porque espera al vigía. Imprime:

| Evidencia | Qué demuestra | Criterio de la rúbrica |
|---|---|---|
| 1 | `docker compose ps` con todos los contenedores | Docker Compose |
| 2 | Tópicos y particiones | Diseño de tópicos |
| 3 | Alta de activo → `ACTIVO_CREADO` → movimiento ALTA | Productor/consumidor, integración |
| 4 | Préstamo → `PRESTAMO_CREADO` → movimiento PRESTAMO **y** notificación (2 grupos) | Productores y consumidores, fan-out |
| 5 | Vigía → `PRESTAMO_VENCIDO` | Procesamiento |
| 6 | Devolución → DEVOLUCION + notificación; kardex completo | Persistencia / resultado verificable |
| 7 | Outbox publicado y claves en Redis con TTL | Idempotencia (Redis) |
| 8 | Simulador re-publica el mismo evento → `DUPLICADO`, conteos sin cambio | Idempotencia |
| 9 | Redis apagado → `WARN` y PostgreSQL frena el duplicado | Manejo de errores |
| 10 | Mensaje inválido → `.DLT` con la causa | Errores y DLT |
| 11 | Grupos de consumidores con LAG 0 | Consumidores funcionales |

Si no quiere apagar Redis durante la demo, use `tools\demo_kafka.ps1 -SinRedisCaido`.

### Simulador de eventos (manual)

```cmd
powershell -ExecutionPolicy Bypass -File tools\simular_evento.ps1 -Tipo duplicado -Buscar PRESTAMO_CREADO
powershell -ExecutionPolicy Bypass -File tools\simular_evento.ps1 -Tipo invalido
powershell -ExecutionPolicy Bypass -File tools\simular_evento.ps1 -Tipo sin-id -Topico eter.activos.eventos.v1
```

## 8. Prueba en Postman

Importe `postman/ETER_4_Microservicios.postman_collection.json` y pegue el token (sección 5).

1. **0. Salud** → los cuatro devuelven `200 {"status":"UP"}`.
2. Carpeta **6. Flujo Kafka (Entrega 2P2)**, en orden:
   1. *Crear activo para el flujo Kafka* → `201` (si ya existe, `409`; cambie `placaKafka` en Variables).
   2. *Kardex del activo (ALTA)* → espere unos 5 s; aparece `ALTA`.
   3. *Crear préstamo vencido del activo* → `201` (guarda `idPrestamo`).
   4. *Kardex del activo (PRESTAMO)* y *Mi bandeja* → aparece el movimiento y la notificación "Préstamo registrado".
   5. Espere unos 30 s: *Mi bandeja* → "Préstamo vencido".
   6. *Registrar devolución del préstamo* → `200`. Repetirla → `409`.
   7. *Kardex del activo (DEVOLUCION)* → ALTA, PRESTAMO y DEVOLUCION.
   8. *Dar de baja el activo* → `204`, y luego *Kardex del activo (BAJA)*.

## 9. Verificación en PostgreSQL y Redis

```cmd
docker exec -it eter-postgres psql -U postgres -d eterdb
```

```sql
-- Productores: eventos del Outbox (published_at lleno = ya está en Kafka)
SELECT event_type, aggregate_key, published_at, publish_attempts, last_error FROM prestamos.outbox_event ORDER BY occurred_at DESC LIMIT 10;
SELECT event_type, aggregate_key, published_at FROM activos.outbox_event ORDER BY occurred_at DESC LIMIT 10;
-- Consumidor inventario: movimientos generados por eventos (idempotency_key = evt:<eventId>)
SELECT id_movimiento, placa_inventario, tipo_movimiento, fecha_movimiento, idempotency_key FROM inventario.movimiento_inventario ORDER BY id_movimiento DESC LIMIT 10;
-- Consumidor notificaciones
SELECT event_id, tipo, titulo, creada_en FROM notificaciones.notificacion ORDER BY creada_en DESC LIMIT 10;
```

```cmd
docker exec -it eter-redis redis-cli --scan --pattern "eter:idem:*"
docker exec -it eter-redis redis-cli GET eter:idem:eter-inventario-v1:<eventId>
docker exec -it eter-redis redis-cli TTL eter:idem:eter-inventario-v1:<eventId>
```

## 10. Pruebas automatizadas

Desde cada servicio (requiere Java 21):

```cmd
cd services\eter-inventario-service
gradlew.bat test
```

| Servicio | Pruebas | Qué cubren |
|---|---:|---|
| activos | 13 | Dominio + `ACTIVO_CREADO` / `ACTIVO_DADO_DE_BAJA` en el Outbox (sin duplicar la baja) |
| préstamos | 21 | Dominio, validación, `PRESTAMO_CREADO` / `PRESTAMO_DEVUELTO` en el Outbox, el publicador no detiene el lote si falla un evento |
| inventario | 13 | Traducción evento → movimiento, duplicados, mensajes inválidos, idempotencia Redis (5 escenarios), respaldo en BD |
| notificaciones | 9 | Traducción evento → notificación, duplicados, mensajes inválidos, idempotencia Redis |

Reporte HTML: `build\reports\tests\test\index.html` de cada servicio.

## 11. Guía para armar el PDF de evidencias

1. Levante todo (sección 3) y tome pantallazo de `docker compose ps` y de Kafka UI (`http://localhost:8092`): pestaña *Topics* (los 4 tópicos) y pestaña *Consumers* (los 2 grupos).
2. Ejecute `tools\demo_kafka.ps1` con la ventana de PowerShell maximizada y tome un pantallazo por cada bloque "EVIDENCIA N".
3. En Kafka UI abra `eter.prestamos.eventos.v1` → *Messages* → un mensaje: pantallazo del JSON (estructura del evento).
4. En Kafka UI abra `eter.prestamos.eventos.v1.DLT` → el mensaje inválido con sus *Headers* (`kafka_dlt-exception-message`).
5. Pantallazo de las pruebas (`gradlew.bat test` o el reporte HTML).
6. El archivo `Evidencias_Entrega2P2.docx` ya trae la estructura y evidencias reales en texto. Reemplace o complemente con sus pantallazos y exporte a PDF.

## 12. Problemas frecuentes

| Síntoma | Causa / solución |
|---|---|
| `port is already allocated` en 8087–8090 | Hay un servicio corriendo con `gradlew bootRun`. Deténgalo o use la opción B. |
| Un servicio queda `unhealthy` o reiniciando | `docker compose logs <servicio>`. Casi siempre PostgreSQL o Kafka no estaban listos; `docker compose restart <servicio>`. |
| No llegan eventos | Revise `SELECT ... FROM prestamos.outbox_event` (¿`published_at` nulo? mire `last_error`) y que `eter-kafka-init` haya terminado con `Exited (0)`. |
| En PowerShell aparecen tildes raras | Es solo la consola. Los datos en la BD están bien; use `tools\demo_kafka.ps1`, que ya decodifica UTF-8. |
| Quiero empezar desde cero | `docker compose down -v` **borra** PostgreSQL y Redis; luego `docker compose up -d`. |

## 13. Apagar el entorno

```cmd
cd infrastructure
docker compose down
```

`docker compose down -v` además borra los volúmenes (datos de PostgreSQL y Redis).
