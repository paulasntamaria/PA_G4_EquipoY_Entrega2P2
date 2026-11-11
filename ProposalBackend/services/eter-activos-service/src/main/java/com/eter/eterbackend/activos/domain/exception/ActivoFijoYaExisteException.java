package com.eter.eterbackend.activos.domain.exception;

public class ActivoFijoYaExisteException extends RuntimeException {

	public ActivoFijoYaExisteException(String mensaje) {
		super(mensaje);
	}
}
