package com.eter.eterbackend.shared.events.infrastructure;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.eter.eterbackend.shared.events.domain.EventoOutbox;
import com.eter.eterbackend.shared.events.domain.EventoOutboxRepository;

/** Publica los eventos pendientes del Outbox; la key (assetKey) mantiene el orden por activo. */
@Component
public class OutboxPublisher {

	private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
	private static final int LOTE = 50;

	private final EventoOutboxRepository eventoOutboxRepository;
	private final KafkaTemplate<String, String> kafkaTemplate;
	private final String topico;

	public OutboxPublisher(EventoOutboxRepository eventoOutboxRepository,
			KafkaTemplate<String, String> kafkaTemplate,
			@Value("${eter.kafka.topic-activos}") String topico) {
		this.eventoOutboxRepository = eventoOutboxRepository;
		this.kafkaTemplate = kafkaTemplate;
		this.topico = topico;
	}

	@Scheduled(fixedDelayString = "${eter.outbox.publicador-delay-ms}")
	public void publicarPendientes() {
		List<EventoOutbox> pendientes = eventoOutboxRepository.buscarPendientes(LOTE);
		if (pendientes.isEmpty()) {
			return;
		}

		for (EventoOutbox evento : pendientes) {
			try {
				// Espera la confirmacion del broker.
				kafkaTemplate.send(topico, evento.aggregateKey(), armarMensaje(evento)).get();
				eventoOutboxRepository.marcarPublicado(evento.eventId());
				log.info("Evento {} ({}) publicado en {} con key {}",
						evento.eventId(), evento.eventType(), topico, evento.aggregateKey());
			} catch (InterruptedException ex) {
				// Apagando: se corta el lote; los pendientes salen en el proximo arranque.
				Thread.currentThread().interrupt();
				eventoOutboxRepository.registrarError(evento.eventId(), "Publicación interrumpida");
				log.warn("Publicación del outbox interrumpida en el evento {}.", evento.eventId());
				return;
			} catch (Exception ex) {
				// Sin confirmacion: queda pendiente y el lote sigue.
				eventoOutboxRepository.registrarError(evento.eventId(), ex.getMessage());
				log.warn("No se pudo publicar el evento {}: {}", evento.eventId(), ex.getMessage());
			}
		}
	}

	/** Sobre del mensaje (ver docs/EVENTOS_KAFKA.md). */
	String armarMensaje(EventoOutbox evento) {
		return """
				{"eventId":"%s","eventType":"%s","eventVersion":1,"source":"eter-activos-service",\
				"assetKey":"%s","nitUniv":"%s","placaInventario":"%s","actor":"%s",\
				"occurredAt":"%s","payload":%s}"""
				.formatted(evento.eventId(), evento.eventType(), evento.aggregateKey(),
						evento.nitUniv(), evento.placaInventario(), evento.actor(),
						evento.occurredAt(), evento.payload());
	}
}
