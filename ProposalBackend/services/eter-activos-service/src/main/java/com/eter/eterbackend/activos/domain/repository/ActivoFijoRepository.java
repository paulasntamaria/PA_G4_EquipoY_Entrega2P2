package com.eter.eterbackend.activos.domain.repository;

import java.util.List;
import java.util.Optional;

import com.eter.eterbackend.activos.domain.model.ActivoFijo;

/** Puerto de salida. El dominio dice QUE necesita; el adaptador JPA dice COMO. */
public interface ActivoFijoRepository {

	boolean existsById(String nitUniv, String placaInventario);

	Optional<ActivoFijo> findById(String nitUniv, String placaInventario);

	List<ActivoFijo> findByNitUniv(String nitUniv);

	ActivoFijo save(ActivoFijo activoFijo);
}
