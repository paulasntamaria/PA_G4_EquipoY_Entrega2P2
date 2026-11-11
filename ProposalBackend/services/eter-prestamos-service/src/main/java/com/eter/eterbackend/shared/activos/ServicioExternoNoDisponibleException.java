package com.eter.eterbackend.shared.activos;

/** activos-service no respondio o respondio algo inesperado (503). */
public class ServicioExternoNoDisponibleException extends RuntimeException {

	public ServicioExternoNoDisponibleException(String mensaje) {
		super(mensaje);
	}
}
