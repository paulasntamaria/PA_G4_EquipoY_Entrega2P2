package com.eter.eterbackend.notificaciones.application.port.in;

/** Lo invoca el consumidor de Kafka cuando llega un evento del topico. */
public interface RegistrarNotificacionUseCase {

	record Command(
			String eventId,
			String eventType,
			String nitUniv,
			String placaInventario,
			String assetKey,
			String destinatarioEmail,
			String titulo,
			String mensaje) {
	}

	/** @return false si el evento ya se habia procesado (consumidor idempotente). */
	boolean registrar(Command command);
}
