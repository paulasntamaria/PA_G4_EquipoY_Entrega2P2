package com.eter.eterbackend.shared.events.infrastructure;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.eter.eterbackend.shared.events.domain.EventoOutbox;
import com.eter.eterbackend.shared.events.domain.EventoOutboxRepository;

@Component
public class OutboxPersistenceAdapter implements EventoOutboxRepository {

	private final SpringDataOutboxRepository repositorio;

	public OutboxPersistenceAdapter(SpringDataOutboxRepository repositorio) {
		this.repositorio = repositorio;
	}

	@Override
	@Transactional
	public boolean guardarSiNoExiste(EventoOutbox evento) {
		// El dedupKey evita reemitir el mismo hecho.
		if (repositorio.existsByDedupKey(evento.dedupKey())) {
			return false;
		}
		repositorio.save(new OutboxEventJpaEntity(evento.dedupKey(), evento.aggregateKey(),
				evento.eventType(), evento.nitUniv(), evento.placaInventario(),
				evento.actor(), evento.payload(), evento.occurredAt()));
		return true;
	}

	@Override
	@Transactional(readOnly = true)
	public List<EventoOutbox> buscarPendientes(int limite) {
		return repositorio.findByPublishedAtIsNullOrderByOccurredAtAsc(Limit.of(limite)).stream()
				.map(e -> new EventoOutbox(e.getEventId(), e.getDedupKey(), e.getAggregateKey(),
						e.getEventType(), e.getNitUniv(), e.getPlacaInventario(), e.getActor(),
						e.getPayload(), e.getOccurredAt(), e.getPublishedAt()))
				.toList();
	}

	@Override
	@Transactional
	public void marcarPublicado(UUID eventId) {
		repositorio.findById(eventId).ifPresent(OutboxEventJpaEntity::marcarPublicado);
	}

	@Override
	@Transactional
	public void registrarError(UUID eventId, String error) {
		repositorio.findById(eventId).ifPresent(e -> e.registrarError(error));
	}
}
