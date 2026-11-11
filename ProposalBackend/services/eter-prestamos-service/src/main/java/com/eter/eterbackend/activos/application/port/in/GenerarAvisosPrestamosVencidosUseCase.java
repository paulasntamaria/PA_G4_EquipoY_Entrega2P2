package com.eter.eterbackend.activos.application.port.in;

/**
 * Lo dispara el vigia programado. Se deja como caso de uso y no como logica
 * dentro del @Scheduled para que el planificador sea solo un adaptador de
 * entrada mas, igual que el controlador REST.
 */
public interface GenerarAvisosPrestamosVencidosUseCase {

	/** @return cuantos eventos nuevos se registraron en el Outbox. */
	int generarAvisos();
}
