package com.eter.eterbackend.activos.application.port.in;

import java.time.LocalDateTime;

import com.eter.eterbackend.activos.domain.model.TipoMovimiento;

/** Registra en el kardex el movimiento de un evento de Kafka. */
public interface RegistrarMovimientoDesdeEventoUseCase {

	record Command(
			String eventId,
			String nitUniv,
			String placaInventario,
			TipoMovimiento tipoMovimiento,
			LocalDateTime fechaMovimiento,
			String numDocumento) {
	}

	/** @return false si el evento ya habia generado su movimiento. */
	boolean registrar(Command command);
}
