package com.eter.eterbackend.activos.domain.exception;

/** Regla de dominio violada; su mensaje se devuelve tal cual en el 400. */
public class DatoInvalidoException extends IllegalArgumentException {

	public DatoInvalidoException(String mensaje) {
		super(mensaje);
	}
}
