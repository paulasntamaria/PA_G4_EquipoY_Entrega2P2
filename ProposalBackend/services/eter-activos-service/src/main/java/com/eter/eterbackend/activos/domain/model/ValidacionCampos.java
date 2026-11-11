package com.eter.eterbackend.activos.domain.model;

import com.eter.eterbackend.activos.domain.exception.DatoInvalidoException;

/** Validaciones comunes del dominio. */
final class ValidacionCampos {

	private ValidacionCampos() {
	}

	static String obligatorio(String valor, String campo, int maxLength) {
		if (valor == null || valor.isBlank()) {
			throw new DatoInvalidoException("El campo %s es obligatorio.".formatted(campo));
		}
		String normalizado = valor.trim();
		if (normalizado.length() > maxLength) {
			throw new DatoInvalidoException(
					"El campo %s excede la longitud máxima de %d caracteres.".formatted(campo, maxLength));
		}
		return normalizado;
	}
}
