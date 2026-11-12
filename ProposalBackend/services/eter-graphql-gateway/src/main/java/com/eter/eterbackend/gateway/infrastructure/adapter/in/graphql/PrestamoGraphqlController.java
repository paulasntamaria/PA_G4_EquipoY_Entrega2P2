package com.eter.eterbackend.gateway.infrastructure.adapter.in.graphql;

import java.util.List;
import java.util.Map;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

import com.eter.eterbackend.gateway.infrastructure.adapter.out.rest.ActivosClient;
import com.eter.eterbackend.gateway.infrastructure.adapter.out.rest.PrestamosClient;
import com.eter.eterbackend.shared.security.ContextoPeticion;

import graphql.schema.DataFetchingEnvironment;

/** Préstamos temporales (eter-prestamos-service). */
@Controller
public class PrestamoGraphqlController {

	private final PrestamosClient prestamos;
	private final ActivosClient activos;

	public PrestamoGraphqlController(PrestamosClient prestamos, ActivosClient activos) {
		this.prestamos = prestamos;
		this.activos = activos;
	}

	@QueryMapping
	public List<Map<String, Object>> prestamos(@Argument String placaInventario, @Argument String estado,
			@Argument Boolean soloVencidos, DataFetchingEnvironment env) {
		ContextoPeticion ctx = ContextoPeticion.de(env);
		List<Map<String, Object>> lista = prestamos.listar(ctx, ctx.nit(), placaInventario, estado);
		return Boolean.TRUE.equals(soloVencidos)
				? lista.stream().filter(p -> Boolean.TRUE.equals(p.get("vencido"))).toList()
				: lista;
	}

	@MutationMapping
	public Map<String, Object> crearPrestamo(DataFetchingEnvironment env) {
		return prestamos.crear(ContextoPeticion.de(env), env.getArgument("input"));
	}

	@MutationMapping
	public Map<String, Object> registrarDevolucion(@Argument String idPrestamo, @Argument String fechaDevolucionReal,
			DataFetchingEnvironment env) {
		return prestamos.registrarDevolucion(ContextoPeticion.de(env), idPrestamo, fechaDevolucionReal);
	}

	/** Composición: el activo de cada préstamo, memoizado (sin N+1). */
	@SchemaMapping(typeName = "Prestamo", field = "activo")
	public Map<String, Object> activo(DataFetchingEnvironment env) {
		return activos.relacionado(ContextoPeticion.de(env), env.getSource());
	}
}
