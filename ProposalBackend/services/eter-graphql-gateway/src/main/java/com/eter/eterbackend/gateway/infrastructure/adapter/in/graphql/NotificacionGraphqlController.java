package com.eter.eterbackend.gateway.infrastructure.adapter.in.graphql;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.graphql.data.method.annotation.SubscriptionMapping;
import org.springframework.stereotype.Controller;

import com.eter.eterbackend.gateway.infrastructure.adapter.out.rest.ActivosClient;
import com.eter.eterbackend.gateway.infrastructure.adapter.out.rest.Json;
import com.eter.eterbackend.gateway.infrastructure.adapter.out.rest.NotificacionesClient;
import com.eter.eterbackend.shared.error.GatewayException;
import com.eter.eterbackend.shared.security.ContextoPeticion;

import graphql.schema.DataFetchingEnvironment;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/** Bandeja del usuario del token (eter-notificaciones-service), también en vivo por WebSocket. */
@Controller
public class NotificacionGraphqlController {

	private final NotificacionesClient notificaciones;
	private final ActivosClient activos;
	private final Duration intervaloSondeo;

	public NotificacionGraphqlController(NotificacionesClient notificaciones, ActivosClient activos,
			@Value("${eter.gateway.poll-notificaciones-ms:5000}") long pollMs) {
		this.notificaciones = notificaciones;
		this.activos = activos;
		this.intervaloSondeo = Duration.ofMillis(pollMs);
	}

	@QueryMapping
	public Map<String, Object> notificaciones(@Argument Integer page, @Argument Integer size, @Argument Boolean soloNoLeidas,
			DataFetchingEnvironment env) {
		return notificaciones.pagina(ContextoPeticion.de(env).token(), page, size, Boolean.TRUE.equals(soloNoLeidas));
	}

	@QueryMapping
	public int notificacionesNoLeidas(DataFetchingEnvironment env) {
		return notificaciones.noLeidas(ContextoPeticion.de(env).token());
	}

	@MutationMapping
	public Map<String, Object> marcarNotificacionLeida(@Argument String idNotificacion, DataFetchingEnvironment env) {
		return notificaciones.marcarLeida(ContextoPeticion.de(env).token(), idNotificacion);
	}

	@MutationMapping
	public int marcarTodasNotificacionesLeidas(DataFetchingEnvironment env) {
		return notificaciones.marcarTodasLeidas(ContextoPeticion.de(env).token());
	}

	/**
	 * Notificaciones nuevas del usuario, por WebSocket. notificaciones-service solo expone REST,
	 * así que se consulta su bandeja cada eter.gateway.poll-notificaciones-ms y se empuja solo lo
	 * nuevo. La primera consulta fija la línea base (lo que ya existía no se reenvía).
	 */
	@SubscriptionMapping
	public Flux<Map<String, Object>> notificacionRecibida(DataFetchingEnvironment env) {
		String token = ContextoPeticion.de(env).token();
		Set<Object> vistas = new HashSet<>();
		AtomicBoolean primeraConsulta = new AtomicBoolean(true);

		return Mono.fromCallable(() -> notificaciones.pagina(token, 0, 50, true))
				.subscribeOn(Schedulers.boundedElastic())
				// Un fallo pasajero se salta; un token vencido o sin permiso cierra la suscripción.
				.onErrorResume(error -> !esDeSesion(error), error -> Mono.empty())
				.repeatWhen(completadas -> completadas.delayElements(intervaloSondeo))
				.concatMapIterable(pagina -> nuevas(pagina, vistas, primeraConsulta));
	}

	@SchemaMapping(typeName = "Notificacion", field = "activo")
	public Map<String, Object> activo(DataFetchingEnvironment env) {
		return activos.relacionado(ContextoPeticion.de(env), env.getSource());
	}

	private static List<Map<String, Object>> nuevas(Map<String, Object> pagina, Set<Object> vistas, AtomicBoolean primera) {
		List<Map<String, Object>> items = new ArrayList<>(Json.lista(pagina.get("items")));
		Collections.reverse(items); // de la más antigua a la más reciente
		List<Map<String, Object>> nuevas = new ArrayList<>();
		for (Map<String, Object> notificacion : items) {
			if (vistas.add(notificacion.get("idNotificacion")) && !primera.get()) {
				nuevas.add(notificacion);
			}
		}
		primera.set(false);
		return nuevas;
	}

	private static boolean esDeSesion(Throwable error) {
		return error instanceof GatewayException ex
				&& (ex.es(GatewayException.NO_AUTENTICADO) || ex.es(GatewayException.PROHIBIDO));
	}
}
