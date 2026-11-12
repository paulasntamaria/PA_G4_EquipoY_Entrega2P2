package com.eter.eterbackend.gateway.infrastructure.adapter.out.rest;

import static com.eter.eterbackend.gateway.infrastructure.adapter.out.rest.MicroservicioRestClient.ruta;

import java.util.Map;

import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

/**
 * eter-notificaciones-service (:8090). Recibe el token directamente porque la suscripción
 * lo usa fuera de una petición HTTP.
 */
@Component
public class NotificacionesClient {

	private static final String RUTA = "/api/v1/notificaciones";

	private final MicroservicioRestClient rest;

	public NotificacionesClient(MicroservicioRestClient rest) {
		this.rest = rest;
	}

	/** Página de la bandeja del usuario del token: { items, page, size, totalElements, noLeidas }. */
	public Map<String, Object> pagina(String token, int page, int size, boolean soloNoLeidas) {
		Map<String, Object> query = Map.of("page", page, "size", size, "soloNoLeidas", soloNoLeidas);
		return Json.objeto(rest.get(Microservicio.NOTIFICACIONES, token, RUTA, query));
	}

	public int noLeidas(String token) {
		return Json.entero(pagina(token, 0, 1, false), "noLeidas");
	}

	public Map<String, Object> marcarLeida(String token, String idNotificacion) {
		return Json.objeto(rest.enviar(Microservicio.NOTIFICACIONES, HttpMethod.PATCH, token,
				ruta(RUTA, idNotificacion, "leida"), null, Map.of()));
	}

	public int marcarTodasLeidas(String token) {
		Object respuesta = rest.enviar(Microservicio.NOTIFICACIONES, HttpMethod.PATCH, token, RUTA + "/leidas", null, Map.of());
		return Json.entero(Json.objeto(respuesta), "noLeidas");
	}
}
