package com.eter.eterbackend.shared.security;

/** El usuario intenta operar sobre una universidad distinta a la de su token (403). */
public class TenantNoAutorizadoException extends RuntimeException {

	public TenantNoAutorizadoException(String mensaje) {
		super(mensaje);
	}
}
