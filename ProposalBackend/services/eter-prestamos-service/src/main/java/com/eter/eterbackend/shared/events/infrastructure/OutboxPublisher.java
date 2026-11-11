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

/**
 * Segunda mitad del patron Outbox. Cada cierto tiempo mira que eventos
 * quedaron sin publicar y los manda a Kafka.
 *
 * La key del mensaje es el assetKey (nitUniv:placaInventario), que es la
 * identidad unica del activo. Asi todos los eventos del mismo activo caen en
 * la misma particion y se procesan en orden.
 */
@Component
public class OutboxPublisher {

	private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
	private static final int LOTE = 50;

	private final EventoOutboxRepository eventoOutboxRepository;
	private final KafkaTemplate<String, String> kafkaTemplate;
	private final String topico;

	public OutboxPublisher(EventoOutboxRepository eventoOutboxRepository,
			KafkaTemplate<String, String> kafkaTemplate,
			@Value("${eter.kafka.topic-prestamos}") String topico) {
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
				// get() para esperar la confirmacion del broker antes de marcarlo publicado.
				kafkaTemplate.send(topico, evento.aggregateKey(), armarMensaje(evento)).get();
				eventoOutboxRepository.marcarPublicado(evento.eventId());
				log.info("Evento {} ({}) publicado en {} con key {}",
						evento.eventId(), evento.eventType(), topico, evento.aggregateKey());
			} catch (InterruptedException ex) {
				// La aplicacion se esta apagando: se restaura la marca de interrupcion y se
				// corta el lote. Los eventos siguen pendientes y salen en el proximo arranque.
				Thread.currentThread().interrupt();
				eventoOutboxRepository.registrarError(evento.eventId(), "Publicación interrumpida");
				log.warn("Publicación del outbox interrumpida en el evento {}.", evento.eventId());
				return;
			} catch (Exception ex) {
				// Kafka no confirmo: el evento queda pendiente y se reintenta en la
				// siguiente vuelta. Un fallo no debe impedir publicar el resto del lote.
				eventoOutboxRepository.registrarError(evento.eventId(), ex.getMessage());
				log.warn("No se pudo publicar el evento {}: {}", evento.eventId(), ex.getMessage());
			}
		}
	}

	/**
	 * Sobre del mensaje: metadatos del evento + el payload del negocio. El formato
	 * esta documentado en docs/EVENTOS_KAFKA.md; si cambia, sube eventVersion.
	 */
	String armarMensaje(EventoOutbox evento) {
		return """
				{"eventId":"%s","eventType":"%s","eventVersion":1,"source":"eter-prestamos-service",\
				"assetKey":"%s","nitUniv":"%s","placaInventario":"%s","actor":"%s",\
				"occurredAt":"%s","payload":%s}"""
				.formatted(evento.eventId(), evento.eventType(), evento.aggregateKey(),
						evento.nitUniv(), evento.placaInventario(), evento.actor(),
						evento.occurredAt(), evento.payload());
	}
}
