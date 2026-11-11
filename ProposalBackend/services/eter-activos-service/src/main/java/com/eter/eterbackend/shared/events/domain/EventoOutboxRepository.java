package com.eter.eterbackend.shared.events.domain;

import java.util.List;
import java.util.UUID;

public interface EventoOutboxRepository {

	/** Devuelve false si el evento ya estaba registrado. */
	boolean guardarSiNoExiste(EventoOutbox evento);

	List<EventoOutbox> buscarPendientes(int limite);

	void marcarPublicado(UUID eventId);

	void registrarError(UUID eventId, String error);
}
