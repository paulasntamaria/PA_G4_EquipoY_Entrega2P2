package com.eter.eterbackend.shared.error;

import java.util.LinkedHashMap;
import java.util.Map;

import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;

/**
 * Error que llega al cliente GraphQL con extensions.code (y servicio, status y errores si aplica).
 * El frontend decide qué mostrar según el código.
 */
public class GatewayException extends RuntimeException {

	public static final String NO_AUTENTICADO = "UNAUTHENTICATED";
	public static final String PROHIBIDO = "FORBIDDEN";
	public static final String NO_ENCONTRADO = "NOT_FOUND";
	public static final String CONFLICTO = "CONFLICT";
	public static final String DATOS_INVALIDOS = "BAD_USER_INPUT";
	public static final String NO_DISPONIBLE = "SERVICE_UNAVAILABLE";
	public static final String ERROR_SERVICIO = "DOWNSTREAM_ERROR";

	private final String codigo;
	private final String servicio;
	private final int status;
	private final Object errores;

	private GatewayException(String codigo, String mensaje, String servicio, int status, Object errores) {
		super(mensaje);
		this.codigo = codigo;
		this.servicio = servicio;
		this.status = status;
		this.errores = errores;
	}

	public static GatewayException noAutenticado() {
		return new GatewayException(NO_AUTENTICADO, "Token ausente o invalido.", null, 0, null);
	}

	/** El microservicio no respondió (caído, rechazó la conexión o superó el tiempo de espera). */
	public static GatewayException noDisponible(String servicio) {
		return new GatewayException(NO_DISPONIBLE, "El servicio de %s no responde.".formatted(servicio), servicio, 0, null);
	}

	/**
	 * Respuesta 4xx/5xx de un microservicio. Los errores vienen en problem+json (detail/title);
	 * los de validación traen además "errores" con el detalle por campo.
	 */
	public static GatewayException desdeRespuesta(String servicio, int status, Object cuerpo) {
		Map<?, ?> datos = cuerpo instanceof Map<?, ?> mapa ? mapa : Map.of();
		Object detalle = datos.get("detail") != null ? datos.get("detail") : datos.get("title");
		String mensaje = detalle != null ? detalle.toString() : "Error %d en %s".formatted(status, servicio);
		Object errores = datos.get("errores") != null ? datos.get("errores") : datos.get("errors");
		return new GatewayException(codigoPara(status), mensaje, servicio, status, errores);
	}

	static String codigoPara(int status) {
		return switch (status) {
			case 400, 422 -> DATOS_INVALIDOS;
			case 401 -> NO_AUTENTICADO;
			case 403 -> PROHIBIDO;
			case 404 -> NO_ENCONTRADO;
			case 409 -> CONFLICTO;
			default -> status >= 500 ? NO_DISPONIBLE : ERROR_SERVICIO;
		};
	}

	public boolean es(String otroCodigo) {
		return codigo.equals(otroCodigo);
	}

	public String getCodigo() {
		return codigo;
	}

	/** Convierte la excepción en el error GraphQL que ve el cliente. */
	public GraphQLError comoErrorGraphQL(GraphqlErrorBuilder<?> builder) {
		Map<String, Object> extensiones = new LinkedHashMap<>();
		extensiones.put("code", codigo);
		if (servicio != null) {
			extensiones.put("servicio", servicio);
		}
		if (status > 0) {
			extensiones.put("status", status);
		}
		if (errores != null) {
			extensiones.put("errores", errores);
		}
		return builder.message(getMessage()).extensions(extensiones).build();
	}
}
