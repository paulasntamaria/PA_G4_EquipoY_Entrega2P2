package com.eter.eterbackend.activos.application.port.out;

/**
 * Quien esta haciendo la operacion. Se usa para firmar los eventos de Kafka
 * sin que la capa de aplicacion dependa de Spring Security.
 */
public interface ActorActualPort {

	/** Email del usuario del token, o "sistema" si no hay usuario autenticado. */
	String email();

	/**
	 * Documento del usuario: en ETER el correo institucional es
	 * {numDocumento}@eter.edu.co, asi que es la parte antes de la arroba.
	 */
	default String numDocumento() {
		String email = email();
		int arroba = email.indexOf('@');
		String documento = arroba > 0 ? email.substring(0, arroba) : email;
		return documento.length() > 20 ? documento.substring(0, 20) : documento;
	}
}
