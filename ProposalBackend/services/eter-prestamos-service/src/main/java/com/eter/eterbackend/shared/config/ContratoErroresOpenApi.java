package com.eter.eterbackend.shared.config;

import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;

/**
 * Completa el contrato OpenAPI generado con las respuestas de error.
 * springdoc solo ve el camino feliz (200/201); aqui se agregan a cada operacion
 * los codigos comunes y, por operationId, los propios de cada endpoint.
 */
public final class ContratoErroresOpenApi {

	private static final String ESQUEMA = "ProblemDetail";

	/** Codigos que puede devolver cualquier endpoint protegido. */
	private static final List<String> COMUNES = List.of("400", "401", "403", "500");

	private static final Map<String, String> DESCRIPCIONES = Map.of(
			"400", "Solicitud inválida: validación de campos, formato o regla de negocio",
			"401", "No autenticado: falta el token JWT o no es válido",
			"403", "Acceso denegado: rol sin permiso o universidad distinta a la del token",
			"404", "Recurso no encontrado",
			"409", "Conflicto con el estado actual o con datos ya registrados",
			"500", "Error interno inesperado",
			"503", "Un servicio del que depende esta operación no está disponible");

	private ContratoErroresOpenApi() {
	}

	/**
	 * @param especificos operationId -> codigos extra de esa operacion (404, 409, 503...)
	 */
	public static void documentar(OpenAPI openApi, Map<String, List<String>> especificos) {
		if (openApi.getComponents() == null) {
			openApi.setComponents(new Components());
		}
		openApi.getComponents().addSchemas(ESQUEMA, esquemaProblemDetail());
		if (openApi.getPaths() == null) {
			return;
		}
		openApi.getPaths().values().forEach(ruta -> ruta.readOperations().forEach(operacion -> {
			COMUNES.forEach(codigo -> agregar(operacion, codigo));
			especificos.getOrDefault(operacion.getOperationId(), List.of())
					.forEach(codigo -> agregar(operacion, codigo));
		}));
	}

	private static void agregar(Operation operacion, String codigo) {
		operacion.getResponses().addApiResponse(codigo, new ApiResponse()
				.description(DESCRIPCIONES.getOrDefault(codigo, "Error"))
				.content(new Content().addMediaType("application/problem+json",
						new MediaType().schema(new Schema<>().$ref("#/components/schemas/" + ESQUEMA)))));
	}

	private static Schema<?> esquemaProblemDetail() {
		ObjectSchema error = new ObjectSchema();
		error.addProperty("campo", new StringSchema().example("nitUniv"));
		error.addProperty("mensaje", new StringSchema().example("no debe estar vacío"));

		ObjectSchema problema = new ObjectSchema();
		problema.description("Formato estándar de error (RFC 9457)");
		problema.addProperty("type", new StringSchema().example("about:blank"));
		problema.addProperty("title", new StringSchema().example("Solicitud inválida"));
		problema.addProperty("status", new IntegerSchema().example(400));
		problema.addProperty("detail", new StringSchema().example("nitUniv: no debe estar vacío"));
		problema.addProperty("instance", new StringSchema().example("/api/v1/activos/activos-fijos"));
		problema.addProperty("errores", new ArraySchema().items(error)
				.description("Solo en errores de validación: un elemento por campo inválido"));
		return problema;
	}
}
