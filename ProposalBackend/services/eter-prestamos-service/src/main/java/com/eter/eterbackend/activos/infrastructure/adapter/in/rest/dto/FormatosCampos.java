package com.eter.eterbackend.activos.infrastructure.adapter.in.rest.dto;

/** Formatos (regex) y mensajes de los campos de entrada. */
public final class FormatosCampos {

	private FormatosCampos() {
	}

	/** NIT colombiano: digitos y, opcional, guion + digito de verificacion. */
	public static final String NIT = "^[0-9]{6,15}(-[0-9])?$";
	public static final String NIT_MENSAJE = "debe tener solo dígitos y, opcionalmente, el dígito de verificación (ej. 900123456-1)";

	/** Codigos internos (placa, sede): bloques alfanumericos separados por guion. */
	public static final String CODIGO = "^[A-Za-z0-9]+(-[A-Za-z0-9]+)*$";
	public static final String PLACA_MENSAJE = "solo admite letras, números y guiones (ej. AF-2026-0001)";
	public static final String SEDE_MENSAJE = "solo admite letras, números y guiones (ej. SED-01)";

	/** Cedula, pasaporte o similar: alfanumerico sin espacios ni simbolos. */
	public static final String DOCUMENTO = "^[A-Za-z0-9]{5,20}$";
	public static final String DOCUMENTO_MENSAJE = "debe tener entre 5 y 20 letras o números, sin espacios ni símbolos";

	/** Clasificador UNSPSC: 8 digitos (segmento, familia, clase, producto). */
	public static final String UNSPSC = "^[0-9]{8}$";
	public static final String UNSPSC_MENSAJE = "debe ser un código UNSPSC de 8 dígitos (ej. 43211503)";
}
