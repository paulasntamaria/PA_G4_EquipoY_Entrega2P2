package com.eter.eterbackend.shared.idempotencia;

/** Otra instancia esta procesando el evento; se reintenta. */
public class EventoEnProcesoException extends RuntimeException {

	public EventoEnProcesoException(String mensaje) {
		super(mensaje);
	}
}
