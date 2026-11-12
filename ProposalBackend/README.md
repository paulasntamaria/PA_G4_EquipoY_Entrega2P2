# ETER - Entrega 2P2 / Kafka e integración orientada a eventos

Cuatro microservicios Spring Boot (Java 21) integrados con Kafka:

- `services/eter-activos-service` — puerto 8087 — productor de `eter.activos.eventos.v1`
- `services/eter-prestamos-service` — puerto 8088 — productor de `eter.prestamos.eventos.v1`
- `services/eter-inventario-service` — puerto 8089 — consumidor de ambos tópicos (kardex)
- `services/eter-notificaciones-service` — puerto 8090 — consumidor de préstamos (bandeja)

Delante de ellos, `services/eter-graphql-gateway` (puerto 4000) es el API Gateway GraphQL, también en Spring Boot (Spring for GraphQL): el frontend solo habla con él y él traduce cada operación a las APIs REST de los 4 servicios.

Infraestructura en `infrastructure/docker-compose.yml`: PostgreSQL 16 (5433), Kafka 3.9 (9092), Redis 7.4 (6379, idempotencia de consumidores) y Kafka UI (8092). El mismo compose levanta también los 4 microservicios y el gateway:

```cmd
cd infrastructure
docker compose up -d --build
cd ..
powershell -ExecutionPolicy Bypass -File tools\demo_kafka.ps1
```

| Documento | Contenido |
|---|---|
| **`GUIA_ECOSISTEMA_4_MICROSERVICIOS.md`** | Cómo levantar todo, flujo, demo, Postman, verificación y guía para el PDF |
| **`docs/EVENTOS_KAFKA.md`** | Diseño de tópicos, estructura de eventos, garantías, idempotencia (Redis + BD), errores y DLT |
| `postman/ETER_4_Microservicios.postman_collection.json` | Colección; la carpeta *6. Flujo Kafka (Entrega 2P2)* tiene tests del flujo asíncrono. No incluye el JWT. |
| `tools/demo_kafka.ps1` / `tools/simular_evento.ps1` | Demostración completa y simulador de eventos (duplicados, mensajes inválidos) |
