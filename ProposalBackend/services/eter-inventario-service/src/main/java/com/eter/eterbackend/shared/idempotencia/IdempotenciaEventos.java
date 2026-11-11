package com.eter.eterbackend.shared.idempotencia;

/**
 * Garantiza que un consumidor procese cada evento de Kafka una sola vez.
 *
 * Kafka entrega "al menos una vez": si el consumidor se cae despues de guardar
 * pero antes de confirmar el offset, o si el productor reenvia, el mismo
 * eventId vuelve a llegar. Esta pieza es la que lo detecta.
 */
public interface IdempotenciaEventos {

	enum Resultado {
		/** Primera vez que se ve el evento: la accion se ejecuto. */
		PROCESADO,
		/** El evento ya se habia procesado antes: la accion NO se ejecuto. */
		DUPLICADO,
		/** Redis no respondio: la accion se ejecuto y la proteccion queda en la base de datos. */
		PROCESADO_SIN_REDIS
	}

	/**
	 * Ejecuta la accion solo si el evento no se ha procesado antes.
	 *
	 * @throws EventoEnProcesoException si otra instancia lo esta procesando en este momento
	 *         (el error handler lo reintenta mas tarde).
	 */
	Resultado ejecutarUnaVez(String eventId, Runnable accion);
}
