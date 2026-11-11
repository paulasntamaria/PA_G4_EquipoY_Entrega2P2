package com.eter.eterbackend.notificaciones.application.service;

import java.util.UUID;

import com.eter.eterbackend.notificaciones.application.port.in.ListarNotificacionesUseCase;
import com.eter.eterbackend.notificaciones.application.port.in.MarcarNotificacionLeidaUseCase;
import com.eter.eterbackend.notificaciones.application.port.in.MarcarTodasLeidasUseCase;
import com.eter.eterbackend.notificaciones.application.port.in.RegistrarNotificacionUseCase;
import com.eter.eterbackend.notificaciones.domain.exception.NotificacionNoEncontradaException;
import com.eter.eterbackend.notificaciones.domain.model.Notificacion;
import com.eter.eterbackend.notificaciones.domain.repository.NotificacionRepository;

public class NotificacionService implements
		ListarNotificacionesUseCase,
		MarcarNotificacionLeidaUseCase,
		MarcarTodasLeidasUseCase,
		RegistrarNotificacionUseCase {

	private final NotificacionRepository notificacionRepository;

	public NotificacionService(NotificacionRepository notificacionRepository) {
		this.notificacionRepository = notificacionRepository;
	}

	@Override
	public Pagina listar(String nitUniv, String email, int page, int size, boolean soloNoLeidas) {
		return new Pagina(
				notificacionRepository.buscarBandeja(nitUniv, email, soloNoLeidas, page, size),
				page,
				size,
				notificacionRepository.contarTotal(nitUniv, email, soloNoLeidas),
				notificacionRepository.contarNoLeidas(nitUniv, email));
	}

	@Override
	public Notificacion marcarLeida(UUID idNotificacion, String email) {
		Notificacion notificacion = notificacionRepository.findById(idNotificacion)
				.orElseThrow(() -> new NotificacionNoEncontradaException(
						"No existe la notificación %s.".formatted(idNotificacion)));

		// Si no es suya, 404 para no revelar que existe.
		if (!notificacion.destinatarioEmail().equalsIgnoreCase(email)) {
			throw new NotificacionNoEncontradaException(
					"No existe la notificación %s.".formatted(idNotificacion));
		}

		return notificacionRepository.save(notificacion.marcarLeida());
	}

	@Override
	public long marcarTodasLeidas(String nitUniv, String email) {
		notificacionRepository.marcarTodasLeidas(nitUniv, email);
		return notificacionRepository.contarNoLeidas(nitUniv, email);
	}

	@Override
	public boolean registrar(Command command) {
		Notificacion notificacion = new Notificacion(
				null,
				UUID.fromString(command.eventId()),
				command.nitUniv(),
				command.destinatarioEmail(),
				command.assetKey(),
				command.placaInventario(),
				command.eventType(),
				command.titulo(),
				command.mensaje(),
				false,
				null,
				null);

		return notificacionRepository.crearSiNoExiste(notificacion);
	}
}
