package com.eter.eterbackend.shared.config;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.eter.eterbackend.notificaciones.domain.exception.NotificacionNoEncontradaException;

/** Errores propios de notificaciones. Los comunes (400, 401, 403, 409, 500) vienen de la base. */
@RestControllerAdvice
public class GlobalExceptionHandler extends ManejadorErroresBase {

	@ExceptionHandler(NotificacionNoEncontradaException.class)
	ResponseEntity<Object> noEncontrado(RuntimeException e) {
		return responder(problema(HttpStatus.NOT_FOUND, "Recurso no encontrado", e.getMessage()));
	}
}
