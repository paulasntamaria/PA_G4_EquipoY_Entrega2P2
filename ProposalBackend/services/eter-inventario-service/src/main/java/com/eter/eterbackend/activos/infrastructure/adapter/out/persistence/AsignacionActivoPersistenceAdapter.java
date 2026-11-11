package com.eter.eterbackend.activos.infrastructure.adapter.out.persistence;

import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.eter.eterbackend.activos.domain.model.AsignacionActivo;
import com.eter.eterbackend.activos.domain.repository.AsignacionActivoRepository;

@Component
public class AsignacionActivoPersistenceAdapter implements AsignacionActivoRepository {

	private final SpringDataAsignacionActivoRepository repositorio;

	public AsignacionActivoPersistenceAdapter(SpringDataAsignacionActivoRepository repositorio) {
		this.repositorio = repositorio;
	}

	@Override
	@Transactional(readOnly = true)
	public boolean existsById(String nitUniv, String placaInventario, String numDocumento) {
		return repositorio.existsById(new AsignacionActivoId(nitUniv, placaInventario, numDocumento));
	}

	@Override
	@Transactional(readOnly = true)
	public List<AsignacionActivo> buscar(String nitUniv, String placaInventario, String numDocumento) {
		return repositorio.buscar(nitUniv, placaInventario, numDocumento).stream()
				.map(e -> new AsignacionActivo(e.getNitUniv(), e.getPlacaInventario(),
						e.getNumDocumento(), e.getFechaHoraActa()))
				.toList();
	}

	@Override
	@Transactional
	public AsignacionActivo save(AsignacionActivo asignacion) {
		AsignacionActivoJpaEntity guardada = repositorio.save(new AsignacionActivoJpaEntity(
				asignacion.nitUniv(), asignacion.placaInventario(),
				asignacion.numDocumento(), asignacion.fechaHoraActa()));
		return new AsignacionActivo(guardada.getNitUniv(), guardada.getPlacaInventario(),
				guardada.getNumDocumento(), guardada.getFechaHoraActa());
	}
}
