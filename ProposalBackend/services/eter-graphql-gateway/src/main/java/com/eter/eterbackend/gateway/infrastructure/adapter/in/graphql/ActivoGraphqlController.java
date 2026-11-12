package com.eter.eterbackend.gateway.infrastructure.adapter.in.graphql;

import java.util.List;
import java.util.Map;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

import com.eter.eterbackend.gateway.infrastructure.adapter.out.rest.ActivosClient;
import com.eter.eterbackend.gateway.infrastructure.adapter.out.rest.InventarioClient;
import com.eter.eterbackend.gateway.infrastructure.adapter.out.rest.PrestamosClient;
import com.eter.eterbackend.shared.error.GatewayException;
import com.eter.eterbackend.shared.security.ContextoPeticion;

import graphql.schema.DataFetchingEnvironment;

/**
 * Activos fijos. Los campos de ActivoFijo que viven en otros servicios (préstamos, kardex,
 * asignaciones) solo se consultan si la query los pide.
 */
@Controller
public class ActivoGraphqlController {

	private final ActivosClient activos;
	private final PrestamosClient prestamos;
	private final InventarioClient inventario;

	public ActivoGraphqlController(ActivosClient activos, PrestamosClient prestamos, InventarioClient inventario) {
		this.activos = activos;
		this.prestamos = prestamos;
		this.inventario = inventario;
	}

	@QueryMapping
	public List<Map<String, Object>> activos(@Argument String estado, @Argument String codSede, DataFetchingEnvironment env) {
		ContextoPeticion ctx = ContextoPeticion.de(env);
		return activos.listar(ctx, ctx.nit()).stream()
				.filter(a -> estado == null || estado.equals(a.get("estado")))
				.filter(a -> codSede == null || codSede.equals(a.get("codSede")))
				.toList();
	}

	/** Un activo que no existe responde null (no error), como pide el esquema. */
	@QueryMapping
	public Map<String, Object> activo(@Argument String placaInventario, DataFetchingEnvironment env) {
		ContextoPeticion ctx = ContextoPeticion.de(env);
		try {
			return activos.obtener(ctx, ctx.nit(), placaInventario);
		} catch (GatewayException ex) {
			if (ex.es(GatewayException.NO_ENCONTRADO)) {
				return null;
			}
			throw ex;
		}
	}

	@MutationMapping
	public Map<String, Object> crearActivo(DataFetchingEnvironment env) {
		return activos.crear(ContextoPeticion.de(env), env.getArgument("input"));
	}

	@MutationMapping
	public Map<String, Object> actualizarActivo(@Argument String placaInventario, DataFetchingEnvironment env) {
		return activos.actualizar(ContextoPeticion.de(env), placaInventario, env.getArgument("input"));
	}

	@MutationMapping
	public Map<String, Object> darDeBajaActivo(@Argument String placaInventario, DataFetchingEnvironment env) {
		return activos.darDeBaja(ContextoPeticion.de(env), placaInventario);
	}

	// Campos de ActivoFijo que vienen de otros microservicios

	@SchemaMapping(typeName = "ActivoFijo", field = "asignaciones")
	public List<Map<String, Object>> asignaciones(DataFetchingEnvironment env) {
		Map<String, Object> activo = env.getSource();
		return inventario.asignaciones(ContextoPeticion.de(env), nit(activo), placa(activo), null);
	}

	@SchemaMapping(typeName = "ActivoFijo", field = "movimientos")
	public List<Map<String, Object>> movimientos(@Argument String tipo, DataFetchingEnvironment env) {
		Map<String, Object> activo = env.getSource();
		return inventario.movimientos(ContextoPeticion.de(env), nit(activo), placa(activo), tipo);
	}

	@SchemaMapping(typeName = "ActivoFijo", field = "prestamos")
	public List<Map<String, Object>> prestamos(@Argument String estado, DataFetchingEnvironment env) {
		Map<String, Object> activo = env.getSource();
		return prestamos.listar(ContextoPeticion.de(env), nit(activo), placa(activo), estado);
	}

	@SchemaMapping(typeName = "ActivoFijo", field = "prestamoVigente")
	public Map<String, Object> prestamoVigente(DataFetchingEnvironment env) {
		Map<String, Object> activo = env.getSource();
		List<Map<String, Object>> vigentes = prestamos.listar(ContextoPeticion.de(env), nit(activo), placa(activo), "VIGENTE");
		return vigentes.isEmpty() ? null : vigentes.get(0);
	}

	private static String nit(Map<String, Object> activo) {
		return String.valueOf(activo.get("nitUniv"));
	}

	private static String placa(Map<String, Object> activo) {
		return String.valueOf(activo.get("placaInventario"));
	}
}
