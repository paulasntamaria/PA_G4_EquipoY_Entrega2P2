package com.eter.eterbackend.activos.infrastructure.adapter.in.rest;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.eter.eterbackend.activos.application.port.in.CrearPrestamoTemporalUseCase;
import com.eter.eterbackend.activos.application.port.in.ListarPrestamosUseCase;
import com.eter.eterbackend.activos.application.port.in.RegistrarDevolucionPrestamoUseCase;
import com.eter.eterbackend.activos.domain.model.EstadoPrestamo;
import com.eter.eterbackend.activos.infrastructure.adapter.in.rest.dto.ActivosDtos;
import com.eter.eterbackend.activos.infrastructure.adapter.in.rest.dto.FormatosCampos;
import com.eter.eterbackend.activos.infrastructure.adapter.in.rest.mapper.ActivosRestMapper;
import com.eter.eterbackend.shared.security.SecurityConfig;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;

@Tag(name = "Prestamos Temporales")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping(value = "/api/v1/activos/prestamos", produces = MediaType.APPLICATION_JSON_VALUE)
public class PrestamoTemporalController {

	private final CrearPrestamoTemporalUseCase crearPrestamoTemporalUseCase;
	private final ListarPrestamosUseCase listarPrestamosUseCase;
	private final RegistrarDevolucionPrestamoUseCase registrarDevolucionPrestamoUseCase;
	private final ActivosRestMapper mapper;

	public PrestamoTemporalController(CrearPrestamoTemporalUseCase crearPrestamoTemporalUseCase,
			ListarPrestamosUseCase listarPrestamosUseCase,
			RegistrarDevolucionPrestamoUseCase registrarDevolucionPrestamoUseCase,
			ActivosRestMapper mapper) {
		this.crearPrestamoTemporalUseCase = crearPrestamoTemporalUseCase;
		this.listarPrestamosUseCase = listarPrestamosUseCase;
		this.registrarDevolucionPrestamoUseCase = registrarDevolucionPrestamoUseCase;
		this.mapper = mapper;
	}

	@Operation(operationId = "listarPrestamos", summary = "Listar préstamos temporales")
	@GetMapping
	public ResponseEntity<List<ActivosDtos.PrestamoTemporalResponse>> listar(
			@RequestParam("nitUniv") String nitUniv,
			@RequestParam(value = "placaInventario", required = false) @Pattern(regexp = FormatosCampos.CODIGO, message = FormatosCampos.PLACA_MENSAJE) String placaInventario,
			@RequestParam(value = "estado", required = false) EstadoPrestamo estado) {
		SecurityConfig.verificarTenant(nitUniv);

		return ResponseEntity.ok(listarPrestamosUseCase.listar(nitUniv, placaInventario, estado)
				.stream().map(mapper::toResponse).toList());
	}

	@Operation(operationId = "crearPrestamoTemporal", summary = "Registrar préstamo temporal",
			description = "El préstamo nace en estado VIGENTE. Responde 409 si el activo ya tiene un préstamo vigente.")
	@PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	public ResponseEntity<ActivosDtos.PrestamoTemporalResponse> crear(
			@Valid @RequestBody ActivosDtos.CrearPrestamoRequest peticion) {
		SecurityConfig.verificarTenant(peticion.nitUniv());

		var creado = crearPrestamoTemporalUseCase.crear(new CrearPrestamoTemporalUseCase.Command(
				peticion.nitUniv(), peticion.placaInventario(), peticion.numDocumento(),
				peticion.fechaSalida(), peticion.fechaDevolucionPactada()));

		return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toResponse(creado));
	}

	@Operation(operationId = "registrarDevolucionPrestamo",
			summary = "Registrar la devolución real de un préstamo",
			description = "Pasa el préstamo a DEVUELTO. Responde 409 si ya fue devuelto o cancelado.")
	@PatchMapping(value = "/{idPrestamo}/devolucion", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<ActivosDtos.PrestamoTemporalResponse> registrarDevolucion(
			@PathVariable UUID idPrestamo,
			@Valid @RequestBody ActivosDtos.RegistrarDevolucionRequest peticion) {

		var devuelto = registrarDevolucionPrestamoUseCase.registrar(
				new RegistrarDevolucionPrestamoUseCase.Command(idPrestamo,
						SecurityConfig.usuarioActual().nitUniv(), peticion.fechaDevolucionReal()));

		return ResponseEntity.ok(mapper.toResponse(devuelto));
	}
}
