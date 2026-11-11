package com.eter.eterbackend.notificaciones.infrastructure.adapter.in.kafka;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashSet;
import java.util.Set;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.eter.eterbackend.notificaciones.application.port.in.RegistrarNotificacionUseCase;
import com.eter.eterbackend.shared.idempotencia.IdempotenciaEventos;

/**
 * Pruebas del consumidor de notificaciones: traduccion del evento, descarte
 * de duplicados y rechazo de mensajes invalidos (que el error handler manda al DLT).
 */
@DisplayName("NotificacionEventConsumer (consumidor Kafka)")
class NotificacionEventConsumerTest {

	private static final String EVENTO = """
			{"eventId":"0b9f3c3e-1d2a-4b5c-8d7e-6f5a4b3c2d1e","eventType":"PRESTAMO_CREADO","eventVersion":1,
			"source":"eter-prestamos-service","assetKey":"900123456-1:AF-2026-0001","nitUniv":"900123456-1",
			"placaInventario":"AF-2026-0001","actor":"79123456","occurredAt":"2026-10-03T15:00:00Z",
			"payload":{"tipo":"PRESTAMO_CREADO","numDocumento":"79123456",
			"destinatarioEmail":"79123456@eter.edu.co","fechaDevolucionPactada":"2026-10-10T18:00"}}""";

	private RegistrarNotificacionUseCase useCase;
	private NotificacionEventConsumer consumidor;

	/** Idempotencia en memoria con la misma semantica que la de Redis. */
	private final Set<String> procesados = new HashSet<>();

	@BeforeEach
	void preparar() {
		useCase = mock(RegistrarNotificacionUseCase.class);
		when(useCase.registrar(any())).thenReturn(true);
		IdempotenciaEventos enMemoria = (eventId, accion) -> {
			if (!procesados.add(eventId)) {
				return IdempotenciaEventos.Resultado.DUPLICADO;
			}
			accion.run();
			return IdempotenciaEventos.Resultado.PROCESADO;
		};
		consumidor = new NotificacionEventConsumer(useCase, enMemoria);
	}

	private static ConsumerRecord<String, String> registro(String valor) {
		return new ConsumerRecord<>("eter.prestamos.eventos.v1", 0, 7L, "900123456-1:AF-2026-0001", valor);
	}

	@Test
	@DisplayName("PRESTAMO_CREADO genera la notificacion para el custodio")
	void traduceEvento() {
		consumidor.consumir(registro(EVENTO));

		var captor = ArgumentCaptor.forClass(RegistrarNotificacionUseCase.Command.class);
		verify(useCase).registrar(captor.capture());
		var command = captor.getValue();
		assertEquals("79123456@eter.edu.co", command.destinatarioEmail());
		assertEquals("PRESTAMO_CREADO", command.eventType());
		assertTrue(command.titulo().startsWith("Préstamo registrado"));
	}

	@Test
	@DisplayName("el mismo evento entregado dos veces crea UNA sola notificacion")
	void duplicadoSeIgnora() {
		consumidor.consumir(registro(EVENTO));
		consumidor.consumir(registro(EVENTO));

		verify(useCase, times(1)).registrar(any());
	}

	@Test
	@DisplayName("JSON roto: excepcion no reintentable (va al DLT) y no toca la base")
	void jsonInvalido() {
		assertThrows(MensajeKafkaInvalidoException.class, () -> consumidor.consumir(registro("{esto no es json")));
		verify(useCase, never()).registrar(any());
	}

	@Test
	@DisplayName("eventId que no es UUID: mensaje invalido")
	void eventIdInvalido() {
		String malo = EVENTO.replace("0b9f3c3e-1d2a-4b5c-8d7e-6f5a4b3c2d1e", "no-es-uuid");
		assertThrows(MensajeKafkaInvalidoException.class, () -> consumidor.consumir(registro(malo)));
	}
}
