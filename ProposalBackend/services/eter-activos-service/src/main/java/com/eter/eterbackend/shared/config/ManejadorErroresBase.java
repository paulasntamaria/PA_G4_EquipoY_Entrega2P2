package com.eter.eterbackend.shared.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.eter.eterbackend.shared.security.TenantNoAutorizadoException;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.exc.MismatchedInputException;

/** Errores comunes de los 4 servicios como ProblemDetail; la validacion agrega "errores". */
public abstract class ManejadorErroresBase extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(ManejadorErroresBase.class);

	protected static final String SOLICITUD_INVALIDA = "Solicitud inválida";

	// 400: validacion

	/** Errores de @Valid @RequestBody. */
	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		List<Map<String, String>> errores = new ArrayList<>();
		ex.getBindingResult().getFieldErrors().forEach(e -> errores.add(error(e.getField(), e.getDefaultMessage())));
		ex.getBindingResult().getGlobalErrors().forEach(e -> errores.add(error(e.getObjectName(), e.getDefaultMessage())));
		return validacion(errores);
	}

	/** Errores de @RequestParam / @PathVariable. */
	@Override
	protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		List<Map<String, String>> errores = new ArrayList<>();
		ex.getParameterValidationResults().forEach(resultado -> {
			String parametro = resultado.getMethodParameter().getParameterName();
			resultado.getResolvableErrors().forEach(e -> {
				String campo = e instanceof FieldError fe ? fe.getField() : parametro;
				errores.add(error(campo, e.getDefaultMessage()));
			});
		});
		return validacion(errores);
	}

	/** JSON mal formado, fecha invalida o enum inexistente. */
	@Override
	protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		MismatchedInputException tipoIncorrecto = buscarCausa(ex, MismatchedInputException.class);
		if (tipoIncorrecto != null && !tipoIncorrecto.getPath().isEmpty()) {
			String campo = tipoIncorrecto.getPath().stream()
					.map(JacksonException.Reference::getPropertyName)
					.filter(nombre -> nombre != null)
					.collect(Collectors.joining("."));
			return validacion(List.of(error(campo, mensajeTipo(tipoIncorrecto.getTargetType()))));
		}
		return responder(problema(HttpStatus.BAD_REQUEST, SOLICITUD_INVALIDA,
				"El cuerpo de la petición no es un JSON válido."));
	}

	@Override
	protected ResponseEntity<Object> handleMissingServletRequestParameter(MissingServletRequestParameterException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		return validacion(List.of(error(ex.getParameterName(), "es obligatorio")));
	}

	/** Parametro de URL con tipo incorrecto. */
	@Override
	protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		String campo = ex instanceof MethodArgumentTypeMismatchException m ? m.getName() : ex.getPropertyName();
		return validacion(List.of(error(campo, mensajeTipo(ex.getRequiredType()))));
	}

	/** IllegalArgumentException de librerias: su mensaje no se expone. */
	@ExceptionHandler(IllegalArgumentException.class)
	ResponseEntity<Object> argumentoIlegal(IllegalArgumentException ex) {
		log.warn("Argumento inválido no controlado: {}", ex.getMessage());
		return responder(problema(HttpStatus.BAD_REQUEST, SOLICITUD_INVALIDA,
				"La solicitud contiene datos inválidos."));
	}

	// 401 / 403

	@ExceptionHandler(TenantNoAutorizadoException.class)
	ResponseEntity<Object> tenant(TenantNoAutorizadoException ex) {
		return responder(problema(HttpStatus.FORBIDDEN, "Acceso denegado", ex.getMessage()));
	}

	@ExceptionHandler(AccessDeniedException.class)
	ResponseEntity<Object> accesoDenegado(AccessDeniedException ex) {
		return responder(problema(HttpStatus.FORBIDDEN, "Acceso denegado",
				"Su rol no tiene permiso para realizar esta operación."));
	}

	@ExceptionHandler(AuthenticationException.class)
	ResponseEntity<Object> noAutenticado(AuthenticationException ex) {
		return responder(problema(HttpStatus.UNAUTHORIZED, "No autenticado",
				"Debe enviar un token JWT válido en la cabecera Authorization."));
	}

	// 409

	/** Creacion simultanea o restriccion SQL violada. */
	@ExceptionHandler(DataIntegrityViolationException.class)
	ResponseEntity<Object> integridad(DataIntegrityViolationException ex) {
		log.warn("Violación de integridad: {}", ex.getMostSpecificCause().getMessage());
		return responder(problema(HttpStatus.CONFLICT, "Conflicto",
				"La operación entra en conflicto con datos ya registrados."));
	}

	// 500

	/** Cualquier otro error: se registra en el log y el cliente recibe un mensaje neutro. */
	@ExceptionHandler(Exception.class)
	ResponseEntity<Object> inesperado(Exception ex) {
		log.error("Error no controlado", ex);
		return responder(problema(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno",
				"Ocurrió un error inesperado. Intente de nuevo más tarde."));
	}

	// Utilidades

	protected static ProblemDetail problema(HttpStatus estado, String titulo, String detalle) {
		ProblemDetail problema = ProblemDetail.forStatus(estado);
		problema.setTitle(titulo);
		problema.setDetail(detalle);
		return problema;
	}

	protected static ResponseEntity<Object> responder(ProblemDetail problema) {
		return ResponseEntity.status(problema.getStatus()).body(problema);
	}

	private static ResponseEntity<Object> validacion(List<Map<String, String>> recibidos) {
		// Copia mutable: List.of(...) no se puede ordenar.
		List<Map<String, String>> errores = new ArrayList<>(recibidos);
		errores.sort(Comparator.comparing(e -> e.get("campo")));
		ProblemDetail problema = problema(HttpStatus.BAD_REQUEST, SOLICITUD_INVALIDA, errores.stream()
				.map(e -> e.get("campo") + ": " + e.get("mensaje"))
				.collect(Collectors.joining("; ")));
		problema.setProperty("errores", errores);
		return responder(problema);
	}

	private static Map<String, String> error(String campo, String mensaje) {
		Map<String, String> error = new LinkedHashMap<>();
		error.put("campo", campo);
		error.put("mensaje", mensaje);
		return error;
	}

	/** Mensaje segun el tipo esperado, sin nombres de clases. */
	private static String mensajeTipo(Class<?> tipo) {
		if (tipo == null) {
			return "tiene un valor inválido";
		}
		if (tipo.isEnum()) {
			return "valor inválido. Valores permitidos: " + Arrays.stream(tipo.getEnumConstants())
					.map(Object::toString).collect(Collectors.joining(", "));
		}
		return switch (tipo.getSimpleName()) {
			case "LocalDateTime" -> "debe ser una fecha y hora con formato yyyy-MM-ddTHH:mm:ss";
			case "LocalDate" -> "debe ser una fecha con formato yyyy-MM-dd";
			case "UUID" -> "debe ser un identificador UUID válido";
			case "BigDecimal", "Integer", "int", "Long", "long" -> "debe ser un número";
			case "Boolean", "boolean" -> "debe ser true o false";
			default -> "tiene un valor inválido";
		};
	}

	private static <T extends Throwable> T buscarCausa(Throwable ex, Class<T> tipo) {
		for (Throwable actual = ex; actual != null; actual = actual.getCause()) {
			if (tipo.isInstance(actual)) {
				return tipo.cast(actual);
			}
		}
		return null;
	}
}
