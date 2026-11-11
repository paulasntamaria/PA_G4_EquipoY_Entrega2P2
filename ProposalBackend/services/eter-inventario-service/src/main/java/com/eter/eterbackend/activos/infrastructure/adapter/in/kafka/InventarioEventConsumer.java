package com.eter.eterbackend.activos.infrastructure.adapter.in.kafka;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.UUID;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.eter.eterbackend.activos.application.port.in.RegistrarMovimientoDesdeEventoUseCase;
import com.eter.eterbackend.activos.domain.model.TipoMovimiento;
import com.eter.eterbackend.shared.idempotencia.IdempotenciaEventos;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Adaptador de entrada por Kafka del kardex de inventario.
 *
 * Escucha dos topicos y traduce cada hecho de negocio a un movimiento:
 *
 *   eter.prestamos.eventos.v1   PRESTAMO_CREADO      -> PRESTAMO
 *                               PRESTAMO_DEVUELTO    -> DEVOLUCION
 *                               PRESTAMO_VENCIDO     -> (no mueve inventario, se ignora)
 *   eter.activos.eventos.v1     ACTIVO_CREADO        -> ALTA
 *                               ACTIVO_DADO_DE_BAJA  -> BAJA
 *
 * Este consumidor usa su propio grupo (eter-inventario-v1), asi que recibe los
 * mismos eventos de prestamos que notificaciones sin quitarselos.
 */
@Component
public class InventarioEventConsumer {

	private static final Logger log = LoggerFactory.getLogger(InventarioEventConsumer.class);

	private final RegistrarMovimientoDesdeEventoUseCase registrarMovimientoUseCase;
	private final IdempotenciaEventos idempotencia;
	private final ObjectMapper objectMapper = new ObjectMapper();

	public InventarioEventConsumer(RegistrarMovimientoDesdeEventoUseCase registrarMovimientoUseCase,
			IdempotenciaEventos idempotencia) {
		this.registrarMovimientoUseCase = registrarMovimientoUseCase;
		this.idempotencia = idempotencia;
	}

	/** Los errores se relanzan: KafkaErrorConfig decide si reintentar o mandar al DLT. */
	@KafkaListener(topics = { "${eter.kafka.topic-prestamos}", "${eter.kafka.topic-activos}" })
	public void consumir(ConsumerRecord<String, String> registro) {
		JsonNode sobre = leer(registro.value());
		String eventId = eventId(sobre);
		String eventType = obligatorio(sobre, "eventType");

		log.info("Evento recibido {} [{}] topico={} particion={} offset={} key={}",
				eventId, eventType, registro.topic(), registro.partition(), registro.offset(), registro.key());

		TipoMovimiento tipo = tipoMovimiento(eventType);
		if (tipo == null) {
			log.info("Evento {} [{}] no genera movimiento de inventario. Se ignora.", eventId, eventType);
			return;
		}

		JsonNode payload = sobre.path("payload");
		var command = new RegistrarMovimientoDesdeEventoUseCase.Command(
				eventId,
				obligatorio(sobre, "nitUniv"),
				obligatorio(sobre, "placaInventario"),
				tipo,
				fechaMovimiento(tipo, sobre, payload),
				numDocumento(tipo, sobre, payload));

		var resultado = idempotencia.ejecutarUnaVez(eventId, () -> registrar(command));

		if (resultado != IdempotenciaEventos.Resultado.DUPLICADO) {
			log.info("Evento {} procesado ({}). Movimiento {} del activo {}.",
					eventId, resultado, tipo, command.placaInventario());
		}
	}

	private void registrar(RegistrarMovimientoDesdeEventoUseCase.Command command) {
		try {
			if (!registrarMovimientoUseCase.registrar(command)) {
				log.info("Evento {} ya tenía movimiento en PostgreSQL (duplicado frenado por la base).",
						command.eventId());
			}
		} catch (DataIntegrityViolationException ex) {
			// Dos entregas a la vez: el indice uq_movimiento_idempotency_key frena la segunda.
			log.info("Evento {} duplicado frenado por uq_movimiento_idempotency_key.", command.eventId());
		}
	}

	static TipoMovimiento tipoMovimiento(String eventType) {
		return switch (eventType) {
			case "PRESTAMO_CREADO" -> TipoMovimiento.PRESTAMO;
			case "PRESTAMO_DEVUELTO" -> TipoMovimiento.DEVOLUCION;
			case "ACTIVO_CREADO" -> TipoMovimiento.ALTA;
			case "ACTIVO_DADO_DE_BAJA" -> TipoMovimiento.BAJA;
			default -> null;
		};
	}

	/** La fecha del hecho de negocio; si no viene en el payload, la del evento. */
	private LocalDateTime fechaMovimiento(TipoMovimiento tipo, JsonNode sobre, JsonNode payload) {
		String campo = switch (tipo) {
			case PRESTAMO -> "fechaSalida";
			case DEVOLUCION -> "fechaDevolucionReal";
			default -> null;
		};
		if (campo != null && payload.hasNonNull(campo)) {
			try {
				return LocalDateTime.parse(payload.get(campo).asText());
			} catch (DateTimeParseException ex) {
				throw new MensajeKafkaInvalidoException("El campo '%s' no es una fecha válida.".formatted(campo), ex);
			}
		}
		// Las fechas del kardex son hora local (igual que las que digita el usuario),
		// asi que el instante del evento se pasa a la zona del servidor.
		try {
			return OffsetDateTime.parse(obligatorio(sobre, "occurredAt"))
					.atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
		} catch (DateTimeParseException ex) {
			throw new MensajeKafkaInvalidoException("El campo 'occurredAt' no es una fecha válida.", ex);
		}
	}

	/** Prestamos: el custodio. Activos: el documento de quien hizo la operacion. */
	private String numDocumento(TipoMovimiento tipo, JsonNode sobre, JsonNode payload) {
		String campo = (tipo == TipoMovimiento.PRESTAMO || tipo == TipoMovimiento.DEVOLUCION)
				? "numDocumento" : "numDocumentoResponsable";
		String valor = payload.path(campo).asText();
		if (valor.isBlank()) {
			throw new MensajeKafkaInvalidoException("Falta el campo obligatorio 'payload.%s'.".formatted(campo), null);
		}
		return valor;
	}

	private JsonNode leer(String mensaje) {
		if (mensaje == null) {
			throw new MensajeKafkaInvalidoException("El mensaje llegó vacío.", null);
		}
		try {
			return objectMapper.readTree(mensaje);
		} catch (JsonProcessingException ex) {
			throw new MensajeKafkaInvalidoException("El mensaje no es un JSON válido.", ex);
		}
	}

	private String eventId(JsonNode sobre) {
		String eventId = obligatorio(sobre, "eventId");
		try {
			return UUID.fromString(eventId).toString();
		} catch (IllegalArgumentException ex) {
			throw new MensajeKafkaInvalidoException("El eventId '%s' no es un UUID.".formatted(eventId), ex);
		}
	}

	private String obligatorio(JsonNode sobre, String campo) {
		String valor = sobre.path(campo).asText();
		if (valor.isBlank()) {
			throw new MensajeKafkaInvalidoException("Falta el campo obligatorio '%s'.".formatted(campo), null);
		}
		return valor;
	}
}
