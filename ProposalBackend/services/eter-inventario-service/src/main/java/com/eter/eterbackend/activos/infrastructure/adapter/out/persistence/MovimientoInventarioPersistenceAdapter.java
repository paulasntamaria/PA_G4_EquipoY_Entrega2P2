package com.eter.eterbackend.activos.infrastructure.adapter.out.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.eter.eterbackend.activos.domain.model.MovimientoInventario;
import com.eter.eterbackend.activos.domain.model.TipoMovimiento;
import com.eter.eterbackend.activos.domain.repository.MovimientoInventarioRepository;

@Component
public class MovimientoInventarioPersistenceAdapter implements MovimientoInventarioRepository {

	private final SpringDataMovimientoInventarioRepository repositorio;

	public MovimientoInventarioPersistenceAdapter(SpringDataMovimientoInventarioRepository repositorio) {
		this.repositorio = repositorio;
	}

	private MovimientoInventario toDomain(MovimientoInventarioJpaEntity e) {
		return new MovimientoInventario(e.getIdMovimiento(), e.getNitUniv(), e.getPlacaInventario(),
				e.getTipoMovimiento(), e.getFechaMovimiento(), e.getNumDocumento());
	}

	@Override
	@Transactional(readOnly = true)
	public List<MovimientoInventario> buscar(String nitUniv, String placaInventario, TipoMovimiento tipoMovimiento) {
		return repositorio.buscar(nitUniv, placaInventario, tipoMovimiento).stream().map(this::toDomain).toList();
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<MovimientoInventario> findByClaveIdempotencia(String nitUniv, String claveIdempotencia) {
		return repositorio.findByNitUnivAndIdempotencyKey(nitUniv, claveIdempotencia).map(this::toDomain);
	}

	@Override
	@Transactional
	public MovimientoInventario save(MovimientoInventario movimiento, String claveIdempotencia) {
		return toDomain(repositorio.save(new MovimientoInventarioJpaEntity(
				movimiento.idMovimiento(), movimiento.nitUniv(), movimiento.placaInventario(),
				movimiento.tipoMovimiento(), movimiento.fechaMovimiento(), movimiento.numDocumento(),
				claveIdempotencia)));
	}
}
