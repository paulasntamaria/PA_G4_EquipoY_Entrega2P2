package com.eter.eterbackend.activos.application.port.in;

import java.time.LocalDateTime;

import com.eter.eterbackend.activos.domain.model.TipoMovimiento;

/**
 * Lo invoca el consumidor de Kafka: un hecho que ocurrio en otro microservicio
 * (prestamo creado, devolucion, alta o baja del activo) deja su huella en el
 * kardex de inventario sin que nadie tenga que registrarlo a mano.
 */
public interface RegistrarMovimientoDesdeEventoUseCase {

	record Command(
			String eventId,
			String nitUniv,
			String placaInventario,
			TipoMovimiento tipoMovimiento,
			LocalDateTime fechaMovimiento,
			String numDocumento) {
	}

	/** @return false si ese evento ya habia generado su movimiento (consumidor idempotente). */
	boolean registrar(Command command);
}
