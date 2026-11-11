package com.eter.eterbackend.activos.infrastructure.adapter.out.persistence;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.eter.eterbackend.activos.domain.model.EstadoPrestamo;
import com.eter.eterbackend.activos.domain.model.PrestamoTemporal;
import com.eter.eterbackend.activos.domain.repository.PrestamoTemporalRepository;

@Component
public class PrestamoTemporalPersistenceAdapter implements PrestamoTemporalRepository {

	private final SpringDataPrestamoTemporalRepository repositorio;

	public PrestamoTemporalPersistenceAdapter(SpringDataPrestamoTemporalRepository repositorio) {
		this.repositorio = repositorio;
	}

	private PrestamoTemporal toDomain(PrestamoTemporalJpaEntity e) {
		return new PrestamoTemporal(e.getIdPrestamo(), e.getNitUniv(), e.getPlacaInventario(),
				e.getNumDocumento(), e.getFechaSalida(), e.getFechaDevolucionPactada(),
				e.getFechaDevolucionReal(), e.getEstado());
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<PrestamoTemporal> findById(UUID idPrestamo) {
		return repositorio.findById(idPrestamo).map(this::toDomain);
	}

	@Override
	@Transactional(readOnly = true)
	public List<PrestamoTemporal> buscar(String nitUniv, String placaInventario, EstadoPrestamo estado) {
		return repositorio.buscar(nitUniv, placaInventario, estado).stream().map(this::toDomain).toList();
	}

	@Override
	@Transactional(readOnly = true)
	public List<PrestamoTemporal> findVigentesVencidos(LocalDateTime corte) {
		return repositorio.findVigentesVencidos(corte).stream().map(this::toDomain).toList();
	}

	@Override
	@Transactional(readOnly = true)
	public boolean existeVigente(String nitUniv, String placaInventario) {
		return repositorio.existsByNitUnivAndPlacaInventarioAndEstado(
				nitUniv, placaInventario, EstadoPrestamo.VIGENTE);
	}

	@Override
	@Transactional
	public PrestamoTemporal save(PrestamoTemporal prestamo) {
		return toDomain(repositorio.save(new PrestamoTemporalJpaEntity(
				prestamo.idPrestamo(), prestamo.nitUniv(), prestamo.placaInventario(), prestamo.numDocumento(),
				prestamo.fechaSalida(), prestamo.fechaDevolucionPactada(),
				prestamo.fechaDevolucionReal(), prestamo.estado())));
	}
}
