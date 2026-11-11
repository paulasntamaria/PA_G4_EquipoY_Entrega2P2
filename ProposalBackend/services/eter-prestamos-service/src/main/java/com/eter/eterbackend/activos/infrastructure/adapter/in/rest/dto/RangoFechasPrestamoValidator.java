package com.eter.eterbackend.activos.infrastructure.adapter.in.rest.dto;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class RangoFechasPrestamoValidator
		implements ConstraintValidator<RangoFechasPrestamo, ActivosDtos.CrearPrestamoRequest> {

	@Override
	public boolean isValid(ActivosDtos.CrearPrestamoRequest peticion, ConstraintValidatorContext contexto) {
		// Las fechas nulas las reporta @NotNull.
		if (peticion == null || peticion.fechaSalida() == null || peticion.fechaDevolucionPactada() == null) {
			return true;
		}
		if (peticion.fechaDevolucionPactada().isAfter(peticion.fechaSalida())) {
			return true;
		}

		// El error se asocia al campo fechaDevolucionPactada.
		contexto.disableDefaultConstraintViolation();
		contexto.buildConstraintViolationWithTemplate(contexto.getDefaultConstraintMessageTemplate())
				.addPropertyNode("fechaDevolucionPactada")
				.addConstraintViolation();
		return false;
	}
}
