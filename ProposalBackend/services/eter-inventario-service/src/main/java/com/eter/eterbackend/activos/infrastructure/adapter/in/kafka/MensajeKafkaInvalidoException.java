package com.eter.eterbackend.activos.infrastructure.adapter.in.kafka;

/**
 * El mensaje no se puede procesar nunca (JSON roto, campos faltantes...).
 * Reintentarlo no sirve, por eso el manejador de errores lo manda directo al DLT.
 */
public class MensajeKafkaInvalidoException extends RuntimeException {

	public MensajeKafkaInvalidoException(String mensaje, Throwable causa) {
		super(mensaje, causa);
	}
}
