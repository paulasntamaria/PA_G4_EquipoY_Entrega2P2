package com.eter.eterbackend.shared.idempotencia;

/**
 * Otra instancia del mismo grupo tiene tomado el evento (clave PROCESANDO en
 * Redis). Es un error transitorio: el error handler de Kafka reintenta y, para
 * entonces, el evento ya aparece como PROCESADO y se descarta como duplicado.
 */
public class EventoEnProcesoException extends RuntimeException {

	public EventoEnProcesoException(String mensaje) {
		super(mensaje);
	}
}
