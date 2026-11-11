package com.eter.eterbackend.notificaciones.application.port.in;

public interface MarcarTodasLeidasUseCase {

	/** @return cuantas quedan sin leer despues de la operacion. */
	long marcarTodasLeidas(String nitUniv, String email);
}
