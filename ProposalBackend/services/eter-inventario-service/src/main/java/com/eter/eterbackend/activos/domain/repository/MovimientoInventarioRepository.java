package com.eter.eterbackend.activos.domain.repository;

import java.util.List;
import java.util.Optional;

import com.eter.eterbackend.activos.domain.model.MovimientoInventario;
import com.eter.eterbackend.activos.domain.model.TipoMovimiento;

public interface MovimientoInventarioRepository {

	List<MovimientoInventario> buscar(String nitUniv, String placaInventario, TipoMovimiento tipoMovimiento);

	/** Movimiento con esa Idempotency-Key, si existe. */
	Optional<MovimientoInventario> findByClaveIdempotencia(String nitUniv, String claveIdempotencia);

	/** @param claveIdempotencia puede ser null. */
	MovimientoInventario save(MovimientoInventario movimientoInventario, String claveIdempotencia);
}
