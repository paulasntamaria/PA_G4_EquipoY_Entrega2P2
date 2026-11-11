package com.eter.eterbackend.notificaciones.domain.model;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/** Notificacion in-app; solo la crea el consumidor de Kafka. */
public record Notificacion(
		UUID idNotificacion,
		UUID eventId,
		String nitUniv,
		String destinatarioEmail,
		String assetKey,
		String placaInventario,
		String tipo,
		String titulo,
		String mensaje,
		boolean leida,
		OffsetDateTime creadaEn,
		OffsetDateTime leidaEn
) {

	public Notificacion {
		Objects.requireNonNull(eventId, "El campo eventId es obligatorio.");
		Objects.requireNonNull(nitUniv, "El campo nitUniv es obligatorio.");
		Objects.requireNonNull(destinatarioEmail, "El campo destinatarioEmail es obligatorio.");
	}

	public Notificacion marcarLeida() {
		if (leida) {
			return this;
		}
		return new Notificacion(idNotificacion, eventId, nitUniv, destinatarioEmail, assetKey,
				placaInventario, tipo, titulo, mensaje, true, creadaEn, OffsetDateTime.now());
	}
}
