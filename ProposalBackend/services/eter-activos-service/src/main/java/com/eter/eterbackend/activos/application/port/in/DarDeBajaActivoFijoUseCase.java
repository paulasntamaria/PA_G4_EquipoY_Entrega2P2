package com.eter.eterbackend.activos.application.port.in;

public interface DarDeBajaActivoFijoUseCase {

	/** Baja logica: conserva el registro y cambia el estado a BAJA. */
	void darDeBaja(String nitUniv, String placaInventario);
}
