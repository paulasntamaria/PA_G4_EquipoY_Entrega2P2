package com.eter.eterbackend.activos.infrastructure.adapter.in.rest.dto;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class RangoFechasPrestamoValidator
		implements ConstraintValidator<RangoFechasPrestamo, ActivosDtos.CrearPrestamoRequest> {

	@Override
	public boolean isValid(ActivosDtos.CrearPrestamoRequest peticion, ConstraintValidatorContext contexto) {
		// Si falta alguna fecha ya responde @NotNull; aqui solo se compara el rango.
		if (peticion == null || peticion.fechaSalida() == null || peticion.fechaDevolucionPactada() == null) {
			return true;
		}
		if (peticion.fechaDevolucionPactada().isAfter(peticion.fechaSalida())) {
			return true;
		}

		// El error se asocia al campo para que el GlobalExceptionHandler lo reporte
		// como "fechaDevolucionPactada: debe ser posterior a fechaSalida".
		contexto.disableDefaultConstraintViolation();
		contexto.buildConstraintViolationWithTemplate(contexto.getDefaultConstraintMessageTemplate())
				.addPropertyNode("fechaDevolucionPactada")
				.addConstraintViolation();
		return false;
	}
}
