package com.eter.eterbackend.activos.infrastructure.adapter.in.rest.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.Set;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

/**
 * Validacion cruzada de fechas en la peticion de prestamo (Bean Validation),
 * sin levantar Spring.
 */
@DisplayName("CrearPrestamoRequest (validacion cruzada de fechas)")
class CrearPrestamoRequestValidacionTest {

	private static final LocalDateTime SALIDA = LocalDateTime.of(2026, 9, 10, 10, 0);

	private static ValidatorFactory factory;
	private static Validator validator;

	@BeforeAll
	static void iniciar() {
		factory = Validation.buildDefaultValidatorFactory();
		validator = factory.getValidator();
	}

	@AfterAll
	static void cerrar() {
		factory.close();
	}

	private static ActivosDtos.CrearPrestamoRequest peticion(LocalDateTime salida, LocalDateTime pactada) {
		return new ActivosDtos.CrearPrestamoRequest("900123456-1", "AF-2026-0001", "79123456", salida, pactada);
	}

	@Test
	@DisplayName("fecha pactada posterior a la salida es valida")
	void rangoValido() {
		assertTrue(validator.validate(peticion(SALIDA, SALIDA.plusDays(5))).isEmpty());
	}

	@Test
	@DisplayName("fecha pactada anterior a la salida se rechaza sobre fechaDevolucionPactada")
	void pactadaAnterior() {
		Set<ConstraintViolation<ActivosDtos.CrearPrestamoRequest>> errores =
				validator.validate(peticion(SALIDA, SALIDA.minusDays(1)));

		assertEquals(1, errores.size());
		ConstraintViolation<ActivosDtos.CrearPrestamoRequest> error = errores.iterator().next();
		assertEquals("fechaDevolucionPactada", error.getPropertyPath().toString());
		assertEquals("debe ser posterior a fechaSalida", error.getMessage());
	}

	@Test
	@DisplayName("fecha pactada igual a la salida se rechaza")
	void pactadaIgual() {
		assertEquals(1, validator.validate(peticion(SALIDA, SALIDA)).size());
	}

	@Test
	@DisplayName("si falta una fecha solo responde @NotNull, sin error cruzado duplicado")
	void fechaNula() {
		Set<ConstraintViolation<ActivosDtos.CrearPrestamoRequest>> errores =
				validator.validate(peticion(SALIDA, null));

		assertEquals(1, errores.size());
		assertEquals("fechaDevolucionPactada", errores.iterator().next().getPropertyPath().toString());
	}
}
