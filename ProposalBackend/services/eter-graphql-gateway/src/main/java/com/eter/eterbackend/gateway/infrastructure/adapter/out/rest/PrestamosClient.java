package com.eter.eterbackend.gateway.infrastructure.adapter.out.rest;

import static com.eter.eterbackend.gateway.infrastructure.adapter.out.rest.MicroservicioRestClient.ruta;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

import com.eter.eterbackend.shared.security.ContextoPeticion;

/** eter-prestamos-service (:8088). */
@Component
public class PrestamosClient {

	private static final String RUTA = "/api/v1/activos/prestamos";

	private final MicroservicioRestClient rest;

	public PrestamosClient(MicroservicioRestClient rest) {
		this.rest = rest;
	}

	public List<Map<String, Object>> listar(ContextoPeticion ctx, String nit, String placa, String estado) {
		Map<String, Object> query = new HashMap<>();
		query.put("nitUniv", nit);
		query.put("placaInventario", placa);
		query.put("estado", estado);
		return Json.lista(rest.get(Microservicio.PRESTAMOS, ctx.token(), RUTA, query));
	}

	public Map<String, Object> crear(ContextoPeticion ctx, Map<String, Object> input) {
		Map<String, Object> cuerpo = new LinkedHashMap<>(input);
		cuerpo.put("nitUniv", ctx.nit());
		return Json.objeto(rest.enviar(Microservicio.PRESTAMOS, HttpMethod.POST, ctx.token(), RUTA, cuerpo, Map.of()));
	}

	public Map<String, Object> registrarDevolucion(ContextoPeticion ctx, String idPrestamo, String fechaDevolucionReal) {
		Map<String, Object> cuerpo = Map.of("fechaDevolucionReal", fechaDevolucionReal);
		return Json.objeto(rest.enviar(Microservicio.PRESTAMOS, HttpMethod.PATCH, ctx.token(),
				ruta(RUTA, idPrestamo, "devolucion"), cuerpo, Map.of()));
	}
}
