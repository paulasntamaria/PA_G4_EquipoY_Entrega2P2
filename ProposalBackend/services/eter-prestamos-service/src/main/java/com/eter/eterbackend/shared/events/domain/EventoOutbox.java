package com.eter.eterbackend.shared.events.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Evento de negocio que se guarda en la MISMA transaccion que el cambio de
 * datos. Ese es todo el truco del patron Outbox: si la transaccion falla, el
 * evento tampoco queda, y nunca se desincronizan la base y Kafka.
 */
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
