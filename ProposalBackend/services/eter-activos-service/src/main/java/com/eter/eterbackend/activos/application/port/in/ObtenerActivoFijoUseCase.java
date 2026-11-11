package com.eter.eterbackend.activos.application.port.in;

import com.eter.eterbackend.activos.domain.model.ActivoFijo;

public interface ObtenerActivoFijoUseCase {

	ActivoFijo obtener(String nitUniv, String placaInventario);
}
