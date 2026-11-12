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
import com.eter.eterbackend.shared.security.ContextoPeticion;

import graphql.schema.DataFetchingEnvironment;

/** Kardex de movimientos y actas de asignación (eter-inventario-service). */
@Controller
public class InventarioGraphqlController {

	private final InventarioClient inventario;
	private final ActivosClient activos;

	public InventarioGraphqlController(InventarioClient inventario, ActivosClient activos) {
		this.inventario = inventario;
		this.activos = activos;
	}

	@QueryMapping
	public List<Map<String, Object>> asignaciones(@Argument String placaInventario, @Argument String numDocumento,
			DataFetchingEnvironment env) {
		ContextoPeticion ctx = ContextoPeticion.de(env);
		return inventario.asignaciones(ctx, ctx.nit(), placaInventario, numDocumento);
	}

	@QueryMapping
	public List<Map<String, Object>> movimientos(@Argument String placaInventario, @Argument String tipo,
			DataFetchingEnvironment env) {
		ContextoPeticion ctx = ContextoPeticion.de(env);
		return inventario.movimientos(ctx, ctx.nit(), placaInventario, tipo);
	}

	@MutationMapping
	public Map<String, Object> crearAsignacion(DataFetchingEnvironment env) {
		return inventario.crearAsignacion(ContextoPeticion.de(env), env.getArgument("input"));
	}

	@MutationMapping
	public Map<String, Object> registrarMovimiento(DataFetchingEnvironment env) {
		return inventario.registrarMovimiento(ContextoPeticion.de(env), env.getArgument("input"));
	}

	@SchemaMapping(typeName = "Asignacion", field = "activo")
	public Map<String, Object> activoDeAsignacion(DataFetchingEnvironment env) {
		return activos.relacionado(ContextoPeticion.de(env), env.getSource());
	}

	@SchemaMapping(typeName = "Movimiento", field = "activo")
	public Map<String, Object> activoDeMovimiento(DataFetchingEnvironment env) {
		return activos.relacionado(ContextoPeticion.de(env), env.getSource());
	}
}
