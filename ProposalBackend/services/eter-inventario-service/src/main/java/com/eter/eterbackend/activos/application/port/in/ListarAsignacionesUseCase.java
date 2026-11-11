package com.eter.eterbackend.activos.application.port.in;

import java.util.List;

import com.eter.eterbackend.activos.domain.model.AsignacionActivo;

public interface ListarAsignacionesUseCase {

	List<AsignacionActivo> listar(String nitUniv, String placaInventario, String numDocumento);
}
