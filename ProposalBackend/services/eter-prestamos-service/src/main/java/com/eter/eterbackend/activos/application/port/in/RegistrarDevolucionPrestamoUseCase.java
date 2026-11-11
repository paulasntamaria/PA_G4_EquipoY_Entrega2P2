package com.eter.eterbackend.activos.application.port.in;

import java.time.LocalDateTime;
import java.util.UUID;

import com.eter.eterbackend.activos.domain.model.PrestamoTemporal;

public interface RegistrarDevolucionPrestamoUseCase {

	PrestamoTemporal registrar(Command command);

	/** nitUniv sale del JWT. */
	record Command(UUID idPrestamo, String nitUniv, LocalDateTime fechaDevolucionReal) {
	}
}
