package com.eter.eterbackend.activos.application.port.in;

import java.math.BigDecimal;

import com.eter.eterbackend.activos.domain.model.ActivoFijo;
import com.eter.eterbackend.activos.domain.model.EstadoActivo;

public interface CrearActivoFijoUseCase {

	ActivoFijo crear(Command command);

	record Command(
			String nitUniv,
			String placaInventario,
			String descripcion,
			String codUnspsc,
			String codSede,
			BigDecimal valorContable,
			EstadoActivo estado) {
	}
}
