package com.eter.eterbackend.shared.events.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Evento que se guarda en la misma transaccion que el cambio de datos (patron Outbox). */
public record EventoOutbox(
		UUID eventId,
		String dedupKey,
		String aggregateKey,
		String eventType,
		String nitUniv,
		String placaInventario,
		String actor,
		String payload,
		OffsetDateTime occurredAt,
		OffsetDateTime publishedAt
) {

	public static EventoOutbox nuevo(String dedupKey, String nitUniv, String placaInventario,
			String eventType, String actor, String payload) {
		return new EventoOutbox(null, dedupKey, nitUniv + ":" + placaInventario, eventType,
				nitUniv, placaInventario, actor, payload, OffsetDateTime.now(), null);
	}
}
