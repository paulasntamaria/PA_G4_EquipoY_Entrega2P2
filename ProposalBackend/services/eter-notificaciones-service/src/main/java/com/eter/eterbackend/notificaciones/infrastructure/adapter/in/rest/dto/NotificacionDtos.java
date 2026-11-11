package com.eter.eterbackend.notificaciones.infrastructure.adapter.in.rest.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class NotificacionDtos {

	private NotificacionDtos() {
	}

	public record NotificacionResponse(
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
			OffsetDateTime leidaEn) {
	}

	public record NotificacionPageResponse(
			List<NotificacionResponse> items,
			int page,
			int size,
			long totalElements,
			long noLeidas) {
	}

	public record NoLeidasResponse(long noLeidas) {
	}
}
