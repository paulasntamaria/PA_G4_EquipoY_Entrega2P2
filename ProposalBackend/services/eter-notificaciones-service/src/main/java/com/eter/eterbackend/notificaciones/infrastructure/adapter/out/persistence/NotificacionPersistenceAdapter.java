package com.eter.eterbackend.notificaciones.infrastructure.adapter.out.persistence;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.eter.eterbackend.notificaciones.domain.model.Notificacion;
import com.eter.eterbackend.notificaciones.domain.repository.NotificacionRepository;

@Component
public class NotificacionPersistenceAdapter implements NotificacionRepository {

	private final SpringDataNotificacionRepository repositorio;

	public NotificacionPersistenceAdapter(SpringDataNotificacionRepository repositorio) {
		this.repositorio = repositorio;
	}

	private Notificacion toDomain(NotificacionJpaEntity e) {
		return new Notificacion(e.getIdNotificacion(), e.getEventId(), e.getNitUniv(),
				e.getDestinatarioEmail(), e.getAssetKey(), e.getPlacaInventario(), e.getTipo(),
				e.getTitulo(), e.getMensaje(), e.isLeida(), e.getCreadaEn(), e.getLeidaEn());
	}

	@Override
	@Transactional
	public boolean crearSiNoExiste(Notificacion notificacion) {
		// La restriccion unica (event_id, destinatario_email) es la que hace
		// idempotente al consumidor: si Kafka reentrega, no se duplica nada.
		if (repositorio.existsByEventIdAndDestinatarioEmail(
				notificacion.eventId(), notificacion.destinatarioEmail())) {
			return false;
		}
		repositorio.save(new NotificacionJpaEntity(notificacion.eventId(), notificacion.nitUniv(),
				notificacion.destinatarioEmail(), notificacion.assetKey(), notificacion.placaInventario(),
				notificacion.tipo(), notificacion.titulo(), notificacion.mensaje()));
		return true;
	}

	@Override
	@Transactional(readOnly = true)
	public List<Notificacion> buscarBandeja(String nitUniv, String email, boolean soloNoLeidas, int page, int size) {
		return repositorio.buscarBandeja(nitUniv, email, soloNoLeidas, PageRequest.of(page, size))
				.stream().map(this::toDomain).toList();
	}

	@Override
	@Transactional(readOnly = true)
	public long contarTotal(String nitUniv, String email, boolean soloNoLeidas) {
		return repositorio.contarTotal(nitUniv, email, soloNoLeidas);
	}

	@Override
	@Transactional(readOnly = true)
	public long contarNoLeidas(String nitUniv, String email) {
		return repositorio.contarNoLeidas(nitUniv, email);
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<Notificacion> findById(UUID idNotificacion) {
		return repositorio.findById(idNotificacion).map(this::toDomain);
	}

	@Override
	@Transactional
	public Notificacion save(Notificacion notificacion) {
		NotificacionJpaEntity entidad = repositorio.findById(notificacion.idNotificacion()).orElseThrow();
		if (notificacion.leida() && !entidad.isLeida()) {
			entidad.marcarLeida(notificacion.leidaEn());
		}
		return toDomain(repositorio.save(entidad));
	}

	@Override
	@Transactional
	public int marcarTodasLeidas(String nitUniv, String email) {
		return repositorio.marcarTodasLeidas(nitUniv, email, OffsetDateTime.now());
	}
}
