package com.eter.eterbackend.activos.application.port.in;

import java.time.LocalDateTime;

import com.eter.eterbackend.activos.domain.model.MovimientoInventario;
import com.eter.eterbackend.activos.domain.model.TipoMovimiento;

public interface CrearMovimientoInventarioUseCase {

	MovimientoInventario crear(Command command);

	record Command(
			String nitUniv,
			String placaInventario,
			TipoMovimiento tipoMovimiento,
			LocalDateTime fechaMovimiento,
			String numDocumento,
			/** Header Idempotency-Key; null si el cliente no lo envia. */
			String claveIdempotencia) {
	}
}
