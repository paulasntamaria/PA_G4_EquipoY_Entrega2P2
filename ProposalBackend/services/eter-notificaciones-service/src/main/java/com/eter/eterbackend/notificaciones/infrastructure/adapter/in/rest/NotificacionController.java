package com.eter.eterbackend.notificaciones.infrastructure.adapter.in.rest;

import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.eter.eterbackend.notificaciones.application.port.in.ListarNotificacionesUseCase;
import com.eter.eterbackend.notificaciones.application.port.in.MarcarNotificacionLeidaUseCase;
import com.eter.eterbackend.notificaciones.application.port.in.MarcarTodasLeidasUseCase;
import com.eter.eterbackend.notificaciones.domain.model.Notificacion;
import com.eter.eterbackend.notificaciones.infrastructure.adapter.in.rest.dto.NotificacionDtos;
import com.eter.eterbackend.shared.security.SecurityConfig;
import com.eter.eterbackend.shared.security.UsuarioAutenticado;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** Bandeja del usuario; tenant y destinatario salen del JWT. */
@Tag(name = "Notificaciones")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping(value = "/api/v1/notificaciones", produces = MediaType.APPLICATION_JSON_VALUE)
public class NotificacionController {

	private final ListarNotificacionesUseCase listarNotificacionesUseCase;
	private final MarcarNotificacionLeidaUseCase marcarNotificacionLeidaUseCase;
	private final MarcarTodasLeidasUseCase marcarTodasLeidasUseCase;

	public NotificacionController(ListarNotificacionesUseCase listarNotificacionesUseCase,
			MarcarNotificacionLeidaUseCase marcarNotificacionLeidaUseCase,
			MarcarTodasLeidasUseCase marcarTodasLeidasUseCase) {
		this.listarNotificacionesUseCase = listarNotificacionesUseCase;
		this.marcarNotificacionLeidaUseCase = marcarNotificacionLeidaUseCase;
		this.marcarTodasLeidasUseCase = marcarTodasLeidasUseCase;
	}

	@Operation(operationId = "listarMisNotificaciones",
			summary = "Consultar notificaciones del usuario autenticado")
	@GetMapping
	public ResponseEntity<NotificacionDtos.NotificacionPageResponse> listar(
			@RequestParam(value = "page", defaultValue = "0") @Min(0) int page,
			// Maximo 100 por pagina.
			@RequestParam(value = "size", defaultValue = "20") @Min(1) @Max(100) int size,
			@RequestParam(value = "soloNoLeidas", defaultValue = "false") boolean soloNoLeidas) {

		UsuarioAutenticado usuario = SecurityConfig.usuarioActual();

		var pagina = listarNotificacionesUseCase.listar(
				usuario.nitUniv(), usuario.email(), page, size, soloNoLeidas);

		return ResponseEntity.ok(new NotificacionDtos.NotificacionPageResponse(
				pagina.items().stream().map(this::toResponse).toList(),
				pagina.page(), pagina.size(), pagina.totalElements(), pagina.noLeidas()));
	}

	@Operation(operationId = "marcarNotificacionLeida", summary = "Marcar una notificación como leída")
	@PatchMapping("/{idNotificacion}/leida")
	public ResponseEntity<NotificacionDtos.NotificacionResponse> marcarLeida(
			@PathVariable UUID idNotificacion) {

		UsuarioAutenticado usuario = SecurityConfig.usuarioActual();
		return ResponseEntity.ok(toResponse(
				marcarNotificacionLeidaUseCase.marcarLeida(idNotificacion, usuario.email())));
	}

	@Operation(operationId = "marcarTodasNotificacionesLeidas",
			summary = "Marcar como leídas todas las notificaciones del usuario")
	@PatchMapping("/leidas")
	public ResponseEntity<NotificacionDtos.NoLeidasResponse> marcarTodasLeidas() {
		UsuarioAutenticado usuario = SecurityConfig.usuarioActual();
		long noLeidas = marcarTodasLeidasUseCase.marcarTodasLeidas(usuario.nitUniv(), usuario.email());
		return ResponseEntity.ok(new NotificacionDtos.NoLeidasResponse(noLeidas));
	}

	private NotificacionDtos.NotificacionResponse toResponse(Notificacion n) {
		return new NotificacionDtos.NotificacionResponse(n.idNotificacion(), n.eventId(), n.nitUniv(),
				n.destinatarioEmail(), n.assetKey(), n.placaInventario(), n.tipo(), n.titulo(),
				n.mensaje(), n.leida(), n.creadaEn(), n.leidaEn());
	}
}
