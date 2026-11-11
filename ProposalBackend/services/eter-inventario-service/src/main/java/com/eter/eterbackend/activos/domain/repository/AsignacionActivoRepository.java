package com.eter.eterbackend.activos.domain.repository;

import java.util.List;

import com.eter.eterbackend.activos.domain.model.AsignacionActivo;

public interface AsignacionActivoRepository {

	boolean existsById(String nitUniv, String placaInventario, String numDocumento);

	List<AsignacionActivo> buscar(String nitUniv, String placaInventario, String numDocumento);

	AsignacionActivo save(AsignacionActivo asignacionActivo);
}
