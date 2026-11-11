package com.eter.eterbackend.activos.application.port.in;

import java.util.List;

import com.eter.eterbackend.activos.domain.model.ActivoFijo;

public interface ListarActivosFijosUseCase {

	List<ActivoFijo> listar(String nitUniv);
}
