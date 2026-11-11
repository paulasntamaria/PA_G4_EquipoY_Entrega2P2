package com.eter.eterbackend.activos.application.port.in;

import java.time.LocalDateTime;

import com.eter.eterbackend.activos.domain.model.AsignacionActivo;

public interface CrearAsignacionActivoUseCase {

	AsignacionActivo crear(Command command);

	record Command(
			String nitUniv,
			String placaInventario,
			String numDocumento,
			LocalDateTime fechaHoraActa) {
	}
}
