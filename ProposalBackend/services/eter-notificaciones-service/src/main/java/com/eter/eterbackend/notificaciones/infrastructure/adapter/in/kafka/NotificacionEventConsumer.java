package com.eter.eterbackend.notificaciones.infrastructure.adapter.in.kafka;

import java.util.UUID;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.eter.eterbackend.notificaciones.application.port.in.RegistrarNotificacionUseCase;
import com.eter.eterbackend.shared.idempotencia.IdempotenciaEventos;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Convierte los eventos de prestamos en notificaciones. */
@Component
public class NotificacionEventConsumer {

	private static final Logger log = LoggerFactory.getLogger(NotificacionEventConsumer.class);

	private final RegistrarNotificacionUseCase registrarNotificacionUseCase;
	private final IdempotenciaEventos idempotencia;
	private final ObjectMapper objectMapper = new ObjectMapper();

	public NotificacionEventConsumer(RegistrarNotificacionUseCase registrarNotificacionUseCase,
			IdempotenciaEventos idempotencia) {
		this.registrarNotificacionUseCase = registrarNotificacionUseCase;
		this.idempotencia = idempotencia;
	}

	/** Los errores se relanzan a KafkaErrorConfig para no perder el evento. */
	@KafkaListener(topics = "${eter.kafka.topic-prestamos}")
	public void consumir(ConsumerRecord<String, String> registro) {
		JsonNode sobre = leer(registro.value());
		String eventId = eventId(sobre);
		String eventType = obligatorio(sobre, "eventType");

		log.info("Evento recibido {} [{}] topico={} particion={} offset={} key={}",
				eventId, eventType, registro.topic(), registro.partition(), registro.offset(), registro.key());

		JsonNode payload = sobre.path("payload");
		String placa = obligatorio(sobre, "placaInventario");
		String email = payload.path("destinatarioEmail").asText();

		if (email.isBlank()) {
			log.warn("Evento {} sin destinatarioEmail, se ignora.", eventId);
			return;
		}

		var command = new RegistrarNotificacionUseCase.Command(
				eventId,
				eventType,
				obligatorio(sobre, "nitUniv"),
				placa,
				obligatorio(sobre, "assetKey"),
				email,
				titulo(eventType, placa),
				mensaje(eventType, placa, payload));

		var resultado = idempotencia.ejecutarUnaVez(eventId, () -> registrar(command));

		if (resultado != IdempotenciaEventos.Resultado.DUPLICADO) {
			log.info("Evento {} procesado ({}). Notificación para {}.", eventId, resultado, email);
		}
	}

	private void registrar(RegistrarNotificacionUseCase.Command command) {
		try {
			if (!registrarNotificacionUseCase.registrar(command)) {
				log.info("Evento {} ya tenía notificación en PostgreSQL (duplicado frenado por la base).",
						command.eventId());
			}
		} catch (DataIntegrityViolationException ex) {
			// Entrega simultanea: el UNIQUE frena la segunda.
			log.info("Evento {} duplicado frenado por UNIQUE(event_id, destinatario_email).", command.eventId());
		}
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

	private String titulo(String eventType, String placa) {
		return switch (eventType) {
			case "PRESTAMO_CREADO" -> "Préstamo registrado: " + placa;
			case "PRESTAMO_VENCIDO" -> "Préstamo vencido: " + placa;
			case "PRESTAMO_DEVUELTO" -> "Préstamo devuelto: " + placa;
			default -> "Novedad del activo " + placa;
		};
	}

	private String mensaje(String eventType, String placa, JsonNode payload) {
		return switch (eventType) {
			case "PRESTAMO_CREADO" -> "Se registró a su nombre el préstamo del activo %s. Debe devolverlo a más tardar el %s."
					.formatted(placa, payload.path("fechaDevolucionPactada").asText());
			case "PRESTAMO_VENCIDO" -> "El activo %s no fue devuelto en la fecha pactada (%s). Por favor regularice la devolución."
					.formatted(placa, payload.path("fechaDevolucionPactada").asText());
			case "PRESTAMO_DEVUELTO" -> "Se registró la devolución del activo %s.".formatted(placa);
			default -> "El activo %s registró el evento %s.".formatted(placa, eventType);
		};
	}
}
