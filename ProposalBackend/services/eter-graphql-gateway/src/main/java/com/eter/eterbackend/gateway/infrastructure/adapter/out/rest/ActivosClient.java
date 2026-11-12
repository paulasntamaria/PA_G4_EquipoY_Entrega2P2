package com.eter.eterbackend.gateway.infrastructure.adapter.out.rest;

import static com.eter.eterbackend.gateway.infrastructure.adapter.out.rest.MicroservicioRestClient.ruta;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

import com.eter.eterbackend.shared.error.GatewayException;
import com.eter.eterbackend.shared.security.ContextoPeticion;

/** eter-activos-service (:8087). Las lecturas se memoizan por petición. */
@Component
public class ActivosClient {

	private static final String RUTA = "/api/v1/activos/activos-fijos";

	private final MicroservicioRestClient rest;

	public ActivosClient(MicroservicioRestClient rest) {
		this.rest = rest;
	}

	public List<Map<String, Object>> listar(ContextoPeticion ctx, String nit) {
		return ctx.memo("activos:" + nit,
				() -> Json.lista(rest.get(Microservicio.ACTIVOS, ctx.token(), RUTA, Map.of("nitUniv", nit))));
	}

	public Map<String, Object> obtener(ContextoPeticion ctx, String nit, String placa) {
		return ctx.memo("activo:" + nit + ":" + placa,
				() -> Json.objeto(rest.get(Microservicio.ACTIVOS, ctx.token(), ruta(RUTA, nit, placa), Map.of())));
	}

	/** Activo de un registro de otro servicio. Uno borrado no tumba la lista: devuelve null. */
	public Map<String, Object> relacionado(ContextoPeticion ctx, Map<String, Object> registro) {
		Object placa = registro.get("placaInventario");
		if (placa == null) {
			return null;
		}
		try {
			return obtener(ctx, String.valueOf(registro.get("nitUniv")), placa.toString());
		} catch (GatewayException ex) {
			if (ex.es(GatewayException.NO_ENCONTRADO)) {
				return null;
			}
			throw ex;
		}
	}

	public Map<String, Object> crear(ContextoPeticion ctx, Map<String, Object> input) {
		Map<String, Object> cuerpo = new LinkedHashMap<>(input);
		cuerpo.put("nitUniv", ctx.nit());
		Object creado = rest.enviar(Microservicio.ACTIVOS, HttpMethod.POST, ctx.token(), RUTA, cuerpo, Map.of());
		ctx.invalidar("activo");
		return Json.objeto(creado);
	}

	public Map<String, Object> actualizar(ContextoPeticion ctx, String placa, Map<String, Object> input) {
		Object actualizado = rest.enviar(Microservicio.ACTIVOS, HttpMethod.PUT, ctx.token(), ruta(RUTA, ctx.nit(), placa),
				input, Map.of());
		ctx.invalidar("activo");
		return Json.objeto(actualizado);
	}

	/** Baja lógica: el servicio responde 204 y se devuelve el activo ya en estado BAJA. */
	public Map<String, Object> darDeBaja(ContextoPeticion ctx, String placa) {
		String nit = ctx.nit();
		rest.enviar(Microservicio.ACTIVOS, HttpMethod.DELETE, ctx.token(), ruta(RUTA, nit, placa), null, Map.of());
		ctx.invalidar("activo");
		return obtener(ctx, nit, placa);
	}
}
