package com.eter.eterbackend.gateway.infrastructure.adapter.in.graphql;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import com.eter.eterbackend.gateway.infrastructure.adapter.out.rest.ActivosClient;
import com.eter.eterbackend.gateway.infrastructure.adapter.out.rest.NotificacionesClient;
import com.eter.eterbackend.gateway.infrastructure.adapter.out.rest.PrestamosClient;
import com.eter.eterbackend.shared.security.ContextoPeticion;
import com.eter.eterbackend.shared.security.UsuarioAutenticado;

import graphql.schema.DataFetchingEnvironment;

/** Usuario del token y resumen del dashboard. */
@Controller
public class UsuarioGraphqlController {

	/** Un hilo virtual por consulta: el resumen pregunta a 3 servicios a la vez. */
	private static final ExecutorService EN_PARALELO = Executors.newVirtualThreadPerTaskExecutor();

	private final ActivosClient activos;
	private final PrestamosClient prestamos;
	private final NotificacionesClient notificaciones;

	public UsuarioGraphqlController(ActivosClient activos, PrestamosClient prestamos, NotificacionesClient notificaciones) {
		this.activos = activos;
		this.prestamos = prestamos;
		this.notificaciones = notificaciones;
	}

	@QueryMapping
	public Map<String, Object> me(DataFetchingEnvironment env) {
		UsuarioAutenticado usuario = ContextoPeticion.de(env).usuario();
		return Map.of("email", usuario.email(), "nitUniv", usuario.nitUniv(), "roles", usuario.roles());
	}

	/** Cifras del dashboard: activos, préstamos y notificaciones consultados en paralelo. */
	@QueryMapping
	public Map<String, Object> resumen(DataFetchingEnvironment env) {
		ContextoPeticion ctx = ContextoPeticion.de(env);
		String nit = ctx.nit();
		String token = ctx.token();

		var listaActivos = CompletableFuture.supplyAsync(() -> activos.listar(ctx, nit), EN_PARALELO);
		var vigentes = CompletableFuture.supplyAsync(() -> prestamos.listar(ctx, nit, null, "VIGENTE"), EN_PARALELO);
		var noLeidas = CompletableFuture.supplyAsync(() -> notificaciones.noLeidas(token), EN_PARALELO);

		Map<String, Integer> porEstado = new LinkedHashMap<>();
		for (Map<String, Object> activo : listaActivos.join()) {
			porEstado.merge(String.valueOf(activo.get("estado")), 1, Integer::sum);
		}
		List<Map<String, Object>> prestamosVigentes = vigentes.join();

		Map<String, Object> resumen = new LinkedHashMap<>();
		resumen.put("totalActivos", porEstado.values().stream().mapToInt(Integer::intValue).sum());
		resumen.put("activosPorEstado", porEstado.entrySet().stream()
				.map(e -> Map.<String, Object>of("estado", e.getKey(), "total", e.getValue()))
				.toList());
		resumen.put("prestamosVigentes", prestamosVigentes.size());
		resumen.put("prestamosVencidos", prestamosVigentes.stream().filter(p -> Boolean.TRUE.equals(p.get("vencido"))).count());
		resumen.put("notificacionesNoLeidas", noLeidas.join());
		return resumen;
	}
}
