package com.eter.eterbackend.notificaciones.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.eter.eterbackend.notificaciones.domain.model.Notificacion;

public interface NotificacionRepository {

	/** @return false si ya existia una notificacion para ese evento y destinatario. */
	boolean crearSiNoExiste(Notificacion notificacion);

	List<Notificacion> buscarBandeja(String nitUniv, String email, boolean soloNoLeidas, int page, int size);

	long contarTotal(String nitUniv, String email, boolean soloNoLeidas);

	long contarNoLeidas(String nitUniv, String email);

	Optional<Notificacion> findById(UUID idNotificacion);

	Notificacion save(Notificacion notificacion);

	int marcarTodasLeidas(String nitUniv, String email);
}
