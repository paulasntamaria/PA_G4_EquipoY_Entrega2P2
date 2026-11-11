package com.eter.eterbackend.activos.application.port.in;

/** Lo dispara el vigia programado. */
public interface GenerarAvisosPrestamosVencidosUseCase {

	/** @return cuantos eventos nuevos se registraron en el Outbox. */
	int generarAvisos();
}
