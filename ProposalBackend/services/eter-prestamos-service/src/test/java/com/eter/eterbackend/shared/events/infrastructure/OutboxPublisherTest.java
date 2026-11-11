package com.eter.eterbackend.shared.events.infrastructure;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import com.eter.eterbackend.shared.events.domain.EventoOutbox;
import com.eter.eterbackend.shared.events.domain.EventoOutboxRepository;

@DisplayName("OutboxPublisher (productor Kafka)")
class OutboxPublisherTest {

	private static final String TOPICO = "eter.prestamos.eventos.v1";

	private final EventoOutboxRepository repositorio = mock(EventoOutboxRepository.class);
	@SuppressWarnings("unchecked")
	private final KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
	private final OutboxPublisher publicador = new OutboxPublisher(repositorio, kafka, TOPICO);

	@AfterEach
	void limpiarInterrupcion() {
		Thread.interrupted();
	}

	private static EventoOutbox evento(String placa) {
		return new EventoOutbox(UUID.randomUUID(), "PRESTAMO_CREADO:" + placa, "900123456-1:" + placa,
				"PRESTAMO_CREADO", "900123456-1", placa, "79123456", "{\"tipo\":\"PRESTAMO_CREADO\"}",
				OffsetDateTime.now(), null);
	}

	@Test
	@DisplayName("si un evento falla, los demas del lote igual se publican")
	void unFalloNoDetieneElLote() {
		EventoOutbox falla = evento("AF-1");
		EventoOutbox ok = evento("AF-2");
		when(repositorio.buscarPendientes(50)).thenReturn(List.of(falla, ok));
		when(kafka.send(eq(TOPICO), eq(falla.aggregateKey()), anyString()))
				.thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker caído")));
		when(kafka.send(eq(TOPICO), eq(ok.aggregateKey()), anyString()))
				.thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

		publicador.publicarPendientes();

		verify(repositorio).registrarError(eq(falla.eventId()), anyString());
		verify(repositorio, never()).marcarPublicado(falla.eventId());
		verify(repositorio).marcarPublicado(ok.eventId());
		assertFalse(Thread.currentThread().isInterrupted(), "El hilo del scheduler no debe quedar interrumpido");
	}

	@Test
	@DisplayName("el sobre lleva eventId, eventType, version, origen y el payload")
	void sobreDelMensaje() {
		EventoOutbox e = evento("AF-3");

		String json = publicador.armarMensaje(e);

		assertTrue(json.contains("\"eventId\":\"" + e.eventId() + "\""));
		assertTrue(json.contains("\"eventType\":\"PRESTAMO_CREADO\""));
		assertTrue(json.contains("\"eventVersion\":1"));
		assertTrue(json.contains("\"source\":\"eter-prestamos-service\""));
		assertTrue(json.contains("\"payload\":{\"tipo\":\"PRESTAMO_CREADO\"}"));
	}
}
