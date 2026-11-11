package com.eter.eterbackend.activos.infrastructure.adapter.in.kafka;

/** Mensaje que nunca se podra procesar; va directo al DLT. */
public class MensajeKafkaInvalidoException extends RuntimeException {

	public MensajeKafkaInvalidoException(String mensaje, Throwable causa) {
		super(mensaje, causa);
	}
}
