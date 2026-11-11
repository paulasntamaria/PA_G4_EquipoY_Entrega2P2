package com.eter.eterbackend.activos.domain.exception;

/**
 * Un dato viola una regla del dominio (obligatorio, longitud, rango de fechas...).
 * Su mensaje esta escrito para el usuario, por eso se devuelve tal cual en el 400.
 * Hereda de IllegalArgumentException para no cambiar el contrato del dominio.
 */
public class DatoInvalidoException extends IllegalArgumentException {

	public DatoInvalidoException(String mensaje) {
		super(mensaje);
	}
}
