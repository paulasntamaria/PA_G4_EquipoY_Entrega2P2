package com.eter.eterbackend.activos.infrastructure.adapter.in.rest;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.eter.eterbackend.activos.application.port.in.ActualizarActivoFijoUseCase;
import com.eter.eterbackend.activos.application.port.in.CrearActivoFijoUseCase;
import com.eter.eterbackend.activos.application.port.in.DarDeBajaActivoFijoUseCase;
import com.eter.eterbackend.activos.application.port.in.ListarActivosFijosUseCase;
import com.eter.eterbackend.activos.application.port.in.ObtenerActivoFijoUseCase;
import com.eter.eterbackend.activos.infrastructure.adapter.in.rest.dto.ActivosDtos;
import com.eter.eterbackend.activos.infrastructure.adapter.in.rest.dto.FormatosCampos;
import com.eter.eterbackend.activos.infrastructure.adapter.in.rest.mapper.ActivosRestMapper;
import com.eter.eterbackend.shared.security.SecurityConfig;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;

@Tag(name = "Activos Fijos")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping(value = "/api/v1/activos/activos-fijos", produces = MediaType.APPLICATION_JSON_VALUE)
public class ActivoFijoController {

	private final CrearActivoFijoUseCase crearActivoFijoUseCase;
	private final ActualizarActivoFijoUseCase actualizarActivoFijoUseCase;
	private final ObtenerActivoFijoUseCase obtenerActivoFijoUseCase;
	private final ListarActivosFijosUseCase listarActivosFijosUseCase;
	private final DarDeBajaActivoFijoUseCase darDeBajaActivoFijoUseCase;
	private final ActivosRestMapper mapper;

	public ActivoFijoController(CrearActivoFijoUseCase crearActivoFijoUseCase,
			ActualizarActivoFijoUseCase actualizarActivoFijoUseCase,
			ObtenerActivoFijoUseCase obtenerActivoFijoUseCase,
			ListarActivosFijosUseCase listarActivosFijosUseCase,
			DarDeBajaActivoFijoUseCase darDeBajaActivoFijoUseCase,
			ActivosRestMapper mapper) {
		this.crearActivoFijoUseCase = crearActivoFijoUseCase;
		this.actualizarActivoFijoUseCase = actualizarActivoFijoUseCase;
		this.obtenerActivoFijoUseCase = obtenerActivoFijoUseCase;
		this.listarActivosFijosUseCase = listarActivosFijosUseCase;
		this.darDeBajaActivoFijoUseCase = darDeBajaActivoFijoUseCase;
		this.mapper = mapper;
	}

	@Operation(operationId = "listarActivosFijos", summary = "Listar activos fijos por universidad")
	@GetMapping
	public ResponseEntity<List<ActivosDtos.ActivoFijoResponse>> listar(@RequestParam("nitUniv") String nitUniv) {
		SecurityConfig.verificarTenant(nitUniv);
		return ResponseEntity.ok(listarActivosFijosUseCase.listar(nitUniv).stream()
				.map(mapper::toResponse).toList());
	}

	@Operation(operationId = "crearActivoFijo", summary = "Registrar nuevo activo fijo")
	@PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	public ResponseEntity<ActivosDtos.ActivoFijoResponse> crear(
			@Valid @RequestBody ActivosDtos.CrearActivoFijoRequest peticion) {
		SecurityConfig.verificarTenant(peticion.nitUniv());

		var creado = crearActivoFijoUseCase.crear(new CrearActivoFijoUseCase.Command(
				peticion.nitUniv(), peticion.placaInventario(), peticion.descripcion(),
				peticion.codUnspsc(), peticion.codSede(), peticion.valorContable(), peticion.estado()));

		return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toResponse(creado));
	}

	@Operation(operationId = "obtenerActivoFijo", summary = "Obtener activo por clave compuesta")
	@GetMapping("/{nitUniv}/{placaInventario}")
	public ResponseEntity<ActivosDtos.ActivoFijoResponse> obtener(
			@PathVariable String nitUniv,
			@PathVariable @Pattern(regexp = FormatosCampos.CODIGO, message = FormatosCampos.PLACA_MENSAJE) String placaInventario) {
		SecurityConfig.verificarTenant(nitUniv);
		return ResponseEntity.ok(mapper.toResponse(obtenerActivoFijoUseCase.obtener(nitUniv, placaInventario)));
	}

	@Operation(operationId = "actualizarActivoFijo", summary = "Actualizar datos mutables del activo")
	@PutMapping(value = "/{nitUniv}/{placaInventario}", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<ActivosDtos.ActivoFijoResponse> actualizar(
			@PathVariable String nitUniv,
			@PathVariable @Pattern(regexp = FormatosCampos.CODIGO, message = FormatosCampos.PLACA_MENSAJE) String placaInventario,
			@Valid @RequestBody ActivosDtos.ActualizarActivoFijoRequest peticion) {
		SecurityConfig.verificarTenant(nitUniv);

		var actualizado = actualizarActivoFijoUseCase.actualizar(new ActualizarActivoFijoUseCase.Command(
				nitUniv, placaInventario, peticion.descripcion(), peticion.codUnspsc(),
				peticion.codSede(), peticion.valorContable(), peticion.estado()));

		return ResponseEntity.ok(mapper.toResponse(actualizado));
	}

	@Operation(operationId = "darDeBajaActivoFijo", summary = "Dar de baja lógicamente un activo",
			description = "Conserva el registro y cambia el estado a BAJA.")
	@DeleteMapping("/{nitUniv}/{placaInventario}")
	public ResponseEntity<Void> darDeBaja(@PathVariable String nitUniv,
			@PathVariable @Pattern(regexp = FormatosCampos.CODIGO, message = FormatosCampos.PLACA_MENSAJE) String placaInventario) {
		SecurityConfig.verificarTenant(nitUniv);
		darDeBajaActivoFijoUseCase.darDeBaja(nitUniv, placaInventario);
		return ResponseEntity.noContent().build();
	}
}
