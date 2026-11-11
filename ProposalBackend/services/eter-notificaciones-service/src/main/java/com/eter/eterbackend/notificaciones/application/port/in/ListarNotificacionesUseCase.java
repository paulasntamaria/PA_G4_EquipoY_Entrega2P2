package com.eter.eterbackend.notificaciones.application.port.in;

import java.util.List;

import com.eter.eterbackend.notificaciones.domain.model.Notificacion;

public interface ListarNotificacionesUseCase {

	/** El tenant y el destinatario NO son parametros: salen del JWT. */
	Pagina listar(String nitUniv, String email, int page, int size, boolean soloNoLeidas);

	record Pagina(
			List<Notificacion> items,
			int page,
			int size,
			long totalElements,
			long noLeidas) {
	}
}
