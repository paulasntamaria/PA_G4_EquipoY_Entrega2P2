package com.eter.eterbackend.notificaciones.application.port.in;

import java.util.UUID;

import com.eter.eterbackend.notificaciones.domain.model.Notificacion;

public interface MarcarNotificacionLeidaUseCase {

	Notificacion marcarLeida(UUID idNotificacion, String email);
}
