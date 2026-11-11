package com.eter.eterbackend.activos.infrastructure.adapter.in.rest.dto;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Validacion cruzada de la peticion de prestamo: fechaDevolucionPactada debe ser
 * posterior a fechaSalida. Se revisa en la entrada HTTP para responder 400 antes
 * de llamar a activos-service; el dominio (PrestamoTemporal) repite la regla.
 */
@Documented
@Constraint(validatedBy = RangoFechasPrestamoValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface RangoFechasPrestamo {

	String message() default "debe ser posterior a fechaSalida";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};
}
