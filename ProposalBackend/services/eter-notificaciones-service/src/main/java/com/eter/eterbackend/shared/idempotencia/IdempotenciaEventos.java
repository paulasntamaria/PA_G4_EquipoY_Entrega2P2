package com.eter.eterbackend.shared.idempotencia;

/** Garantiza que cada evento de Kafka se procese una sola vez. */
public interface IdempotenciaEventos {

	enum Resultado {
		/** Primera vez que se ve el evento: la accion se ejecuto. */
		PROCESADO,
		/** Ya procesado: la accion NO se ejecuto. */
		DUPLICADO,
		/** Redis no respondio: se ejecuto y la base protege. */
		PROCESADO_SIN_REDIS
	}

	/** Ejecuta la accion solo si el evento es nuevo. */
	Resultado ejecutarUnaVez(String eventId, Runnable accion);
}
