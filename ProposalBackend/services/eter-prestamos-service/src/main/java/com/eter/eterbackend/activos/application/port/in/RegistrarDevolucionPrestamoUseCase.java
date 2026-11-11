package com.eter.eterbackend.activos.application.port.in;

import java.time.LocalDateTime;
import java.util.UUID;

import com.eter.eterbackend.activos.domain.model.PrestamoTemporal;

/** Operacion nueva de la Entrega 2: resuelve el riesgo de la Entrega 1. */
public interface RegistrarDevolucionPrestamoUseCase {

	PrestamoTemporal registrar(Command command);

	/** nitUniv es la universidad del usuario (sale del JWT), no del cuerpo de la peticion. */
	record Command(UUID idPrestamo, String nitUniv, LocalDateTime fechaDevolucionReal) {
	}
}
