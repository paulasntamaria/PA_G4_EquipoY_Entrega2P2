package com.eter.eterbackend.activos.infrastructure.adapter.in.rest;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.eter.eterbackend.activos.application.port.in.CrearMovimientoInventarioUseCase;
import com.eter.eterbackend.activos.application.port.in.ListarMovimientosUseCase;
import com.eter.eterbackend.activos.domain.model.TipoMovimiento;
import com.eter.eterbackend.activos.infrastructure.adapter.in.rest.dto.ActivosDtos;
import com.eter.eterbackend.activos.infrastructure.adapter.in.rest.dto.FormatosCampos;
import com.eter.eterbackend.activos.infrastructure.adapter.in.rest.mapper.ActivosRestMapper;
import com.eter.eterbackend.shared.security.SecurityConfig;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Tag(name = "Movimientos")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping(value = "/api/v1/activos/movimientos-inventario", produces = MediaType.APPLICATION_JSON_VALUE)
public class MovimientoInventarioController {

	private final CrearMovimientoInventarioUseCase crearMovimientoInventarioUseCase;
	private final ListarMovimientosUseCase listarMovimientosUseCase;
	private final ActivosRestMapper mapper;

	public MovimientoInventarioController(CrearMovimientoInventarioUseCase crearMovimientoInventarioUseCase,
			ListarMovimientosUseCase listarMovimientosUseCase, ActivosRestMapper mapper) {
		this.crearMovimientoInventarioUseCase = crearMovimientoInventarioUseCase;
		this.listarMovimientosUseCase = listarMovimientosUseCase;
		this.mapper = mapper;
	}

	@Operation(operationId = "listarMovimientos", summary = "Listar movimientos de inventario")
	@GetMapping
	public ResponseEntity<List<ActivosDtos.MovimientoInventarioResponse>> listar(
			@RequestParam("nitUniv") String nitUniv,
			@RequestParam(value = "placaInventario", required = false) @Pattern(regexp = FormatosCampos.CODIGO, message = FormatosCampos.PLACA_MENSAJE) String placaInventario,
			@RequestParam(value = "tipoMovimiento", required = false) TipoMovimiento tipoMovimiento) {
		SecurityConfig.verificarTenant(nitUniv);

		return ResponseEntity.ok(listarMovimientosUseCase.listar(nitUniv, placaInventario, tipoMovimiento)
				.stream().map(mapper::toResponse).toList());
	}

	@Operation(operationId = "crearMovimientoInventario", summary = "Registrar movimiento append-only",
			description = "Acepta el header opcional Idempotency-Key: si se repite la misma clave, "
					+ "se devuelve el movimiento ya registrado en vez de crear uno nuevo.")
	@PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	public ResponseEntity<ActivosDtos.MovimientoInventarioResponse> crear(
			@RequestHeader(value = "Idempotency-Key", required = false)
			@Size(min = 1, max = 100, message = "Idempotency-Key debe tener entre 1 y 100 caracteres.") String claveIdempotencia,
			@Valid @RequestBody ActivosDtos.CrearMovimientoRequest peticion) {
		SecurityConfig.verificarTenant(peticion.nitUniv());

		var creado = crearMovimientoInventarioUseCase.crear(new CrearMovimientoInventarioUseCase.Command(
				peticion.nitUniv(), peticion.placaInventario(), peticion.tipoMovimiento(),
				peticion.fechaMovimiento(), peticion.numDocumento(), claveIdempotencia));

		return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toResponse(creado));
	}
}
