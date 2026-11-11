package com.eter.eterbackend.shared.config;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.eter.eterbackend.activos.domain.exception.ActivoFijoNoEncontradoException;
import com.eter.eterbackend.activos.domain.exception.DatoInvalidoException;
import com.eter.eterbackend.activos.domain.exception.PrestamoNoEncontradoException;
import com.eter.eterbackend.activos.domain.exception.PrestamoNoVigenteException;
import com.eter.eterbackend.activos.domain.exception.PrestamoVigenteYaExisteException;
import com.eter.eterbackend.shared.activos.ServicioExternoNoDisponibleException;

/** Errores propios de prestamos. Los comunes (400, 401, 403, 409, 500) vienen de la base. */
@RestControllerAdvice
public class GlobalExceptionHandler extends ManejadorErroresBase {

	@ExceptionHandler({ ActivoFijoNoEncontradoException.class, PrestamoNoEncontradoException.class })
	ResponseEntity<Object> noEncontrado(RuntimeException e) {
		return responder(problema(HttpStatus.NOT_FOUND, "Recurso no encontrado", e.getMessage()));
	}

	@ExceptionHandler({ PrestamoNoVigenteException.class, PrestamoVigenteYaExisteException.class })
	ResponseEntity<Object> conflicto(RuntimeException e) {
		return responder(problema(HttpStatus.CONFLICT, "Conflicto", e.getMessage()));
	}

	@ExceptionHandler(DatoInvalidoException.class)
	ResponseEntity<Object> datoInvalido(DatoInvalidoException e) {
		// Regla de dominio: su mensaje esta escrito para el usuario y se devuelve tal cual.
		return responder(problema(HttpStatus.BAD_REQUEST, SOLICITUD_INVALIDA, e.getMessage()));
	}

	@ExceptionHandler(ServicioExternoNoDisponibleException.class)
	ResponseEntity<Object> servicioNoDisponible(RuntimeException e) {
		return responder(problema(HttpStatus.SERVICE_UNAVAILABLE, "Servicio no disponible", e.getMessage()));
	}
}
