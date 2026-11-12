package com.eter.eterbackend.gateway.infrastructure.adapter.out.rest;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

import com.eter.eterbackend.shared.security.ContextoPeticion;

/** eter-inventario-service (:8089): actas de asignación y kardex de movimientos. */
@Component
public class InventarioClient {

	private static final String RUTA_ASIGNACIONES = "/api/v1/activos/asignaciones";
	private static final String RUTA_MOVIMIENTOS = "/api/v1/activos/movimientos-inventario";

	private final MicroservicioRestClient rest;

	public InventarioClient(MicroservicioRestClient rest) {
		this.rest = rest;
	}

	public List<Map<String, Object>> asignaciones(ContextoPeticion ctx, String nit, String placa, String numDocumento) {
		Map<String, Object> query = new HashMap<>();
		query.put("nitUniv", nit);
		query.put("placaInventario", placa);
		query.put("numDocumento", numDocumento);
		return Json.lista(rest.get(Microservicio.INVENTARIO, ctx.token(), RUTA_ASIGNACIONES, query));
	}

	public List<Map<String, Object>> movimientos(ContextoPeticion ctx, String nit, String placa, String tipo) {
		Map<String, Object> query = new HashMap<>();
		query.put("nitUniv", nit);
		query.put("placaInventario", placa);
		query.put("tipoMovimiento", tipo);
		return Json.lista(rest.get(Microservicio.INVENTARIO, ctx.token(), RUTA_MOVIMIENTOS, query));
	}

	public Map<String, Object> crearAsignacion(ContextoPeticion ctx, Map<String, Object> input) {
		Map<String, Object> cuerpo = new LinkedHashMap<>(input);
		cuerpo.put("nitUniv", ctx.nit());
		return Json.objeto(rest.enviar(Microservicio.INVENTARIO, HttpMethod.POST, ctx.token(), RUTA_ASIGNACIONES, cuerpo, Map.of()));
	}

	/** La idempotencyKey viaja en la cabecera Idempotency-Key: repetirla devuelve el movimiento ya registrado. */
	public Map<String, Object> registrarMovimiento(ContextoPeticion ctx, Map<String, Object> input) {
		Map<String, Object> cuerpo = new LinkedHashMap<>(input);
		Object clave = cuerpo.remove("idempotencyKey");
		cuerpo.put("nitUniv", ctx.nit());
		Map<String, String> cabeceras = clave != null ? Map.of("Idempotency-Key", clave.toString()) : Map.of();
		return Json.objeto(rest.enviar(Microservicio.INVENTARIO, HttpMethod.POST, ctx.token(), RUTA_MOVIMIENTOS, cuerpo, cabeceras));
	}
}
