package com.eter.eterbackend.activos.infrastructure.adapter.in.rest;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.eter.eterbackend.activos.application.port.in.CrearAsignacionActivoUseCase;
import com.eter.eterbackend.activos.application.port.in.ListarAsignacionesUseCase;
import com.eter.eterbackend.activos.infrastructure.adapter.in.rest.dto.ActivosDtos;
import com.eter.eterbackend.activos.infrastructure.adapter.in.rest.dto.FormatosCampos;
import com.eter.eterbackend.activos.infrastructure.adapter.in.rest.mapper.ActivosRestMapper;
import com.eter.eterbackend.shared.security.SecurityConfig;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;

@Tag(name = "Asignaciones")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping(value = "/api/v1/activos/asignaciones", produces = MediaType.APPLICATION_JSON_VALUE)
public class AsignacionActivoController {

	private final CrearAsignacionActivoUseCase crearAsignacionActivoUseCase;
	private final ListarAsignacionesUseCase listarAsignacionesUseCase;
	private final ActivosRestMapper mapper;

	public AsignacionActivoController(CrearAsignacionActivoUseCase crearAsignacionActivoUseCase,
			ListarAsignacionesUseCase listarAsignacionesUseCase, ActivosRestMapper mapper) {
		this.crearAsignacionActivoUseCase = crearAsignacionActivoUseCase;
		this.listarAsignacionesUseCase = listarAsignacionesUseCase;
		this.mapper = mapper;
	}

	@Operation(operationId = "listarAsignaciones", summary = "Listar asignaciones por universidad")
	@GetMapping
	public ResponseEntity<List<ActivosDtos.AsignacionActivoResponse>> listar(
			@RequestParam("nitUniv") String nitUniv,
			@RequestParam(value = "placaInventario", required = false) @Pattern(regexp = FormatosCampos.CODIGO, message = FormatosCampos.PLACA_MENSAJE) String placaInventario,
			@RequestParam(value = "numDocumento", required = false) @Pattern(regexp = FormatosCampos.DOCUMENTO, message = FormatosCampos.DOCUMENTO_MENSAJE) String numDocumento) {
		SecurityConfig.verificarTenant(nitUniv);

		return ResponseEntity.ok(listarAsignacionesUseCase.listar(nitUniv, placaInventario, numDocumento)
				.stream().map(mapper::toResponse).toList());
	}

	@Operation(operationId = "crearAsignacionActivo", summary = "Registrar asignación de custodia")
	@PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	public ResponseEntity<ActivosDtos.AsignacionActivoResponse> crear(
			@Valid @RequestBody ActivosDtos.CrearAsignacionRequest peticion) {
		SecurityConfig.verificarTenant(peticion.nitUniv());

		var creada = crearAsignacionActivoUseCase.crear(new CrearAsignacionActivoUseCase.Command(
				peticion.nitUniv(), peticion.placaInventario(),
				peticion.numDocumento(), peticion.fechaHoraActa()));

		return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toResponse(creada));
	}
}
