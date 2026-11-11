package com.eter.eterbackend.activos.infrastructure.adapter.in.kafka;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.Set;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.eter.eterbackend.activos.application.port.in.RegistrarMovimientoDesdeEventoUseCase;
import com.eter.eterbackend.activos.domain.model.TipoMovimiento;
import com.eter.eterbackend.shared.idempotencia.IdempotenciaEventos;

@DisplayName("InventarioEventConsumer (consumidor Kafka)")
class InventarioEventConsumerTest {

	private RegistrarMovimientoDesdeEventoUseCase useCase;
	private InventarioEventConsumer consumidor;
	private final Set<String> procesados = new HashSet<>();

	@BeforeEach
	void preparar() {
		useCase = mock(RegistrarMovimientoDesdeEventoUseCase.class);
		when(useCase.registrar(any())).thenReturn(true);
		IdempotenciaEventos enMemoria = (eventId, accion) -> {
			if (!procesados.add(eventId)) {
				return IdempotenciaEventos.Resultado.DUPLICADO;
			}
			accion.run();
			return IdempotenciaEventos.Resultado.PROCESADO;
		};
		consumidor = new InventarioEventConsumer(useCase, enMemoria);
	}

	private static String evento(String eventId, String tipo, String payload) {
		return """
				{"eventId":"%s","eventType":"%s","eventVersion":1,"assetKey":"900123456-1:AF-2026-0001",
				"nitUniv":"900123456-1","placaInventario":"AF-2026-0001","actor":"79123456",
				"occurredAt":"2026-10-03T15:00:00.123-05:00","payload":%s}""".formatted(eventId, tipo, payload);
	}

	private static ConsumerRecord<String, String> registro(String topico, String valor) {
		return new ConsumerRecord<>(topico, 1, 3L, "900123456-1:AF-2026-0001", valor);
	}

	private RegistrarMovimientoDesdeEventoUseCase.Command unicoComando() {
		var captor = ArgumentCaptor.forClass(RegistrarMovimientoDesdeEventoUseCase.Command.class);
		verify(useCase).registrar(captor.capture());
		return captor.getValue();
	}

	@Test
	@DisplayName("PRESTAMO_CREADO -> movimiento PRESTAMO con la fecha de salida y el custodio")
	void prestamoCreado() {
		consumidor.consumir(registro("eter.prestamos.eventos.v1", evento(
				"11111111-1111-4111-8111-111111111111", "PRESTAMO_CREADO",
				"{\"numDocumento\":\"79123456\",\"fechaSalida\":\"2026-10-03T08:00\",\"fechaDevolucionReal\":null}")));

		var command = unicoComando();
		assertEquals(TipoMovimiento.PRESTAMO, command.tipoMovimiento());
		assertEquals(LocalDateTime.of(2026, 10, 3, 8, 0), command.fechaMovimiento());
		assertEquals("79123456", command.numDocumento());
	}

	@Test
	@DisplayName("PRESTAMO_DEVUELTO -> movimiento DEVOLUCION con la fecha real")
	void prestamoDevuelto() {
		consumidor.consumir(registro("eter.prestamos.eventos.v1", evento(
				"22222222-2222-4222-8222-222222222222", "PRESTAMO_DEVUELTO",
				"{\"numDocumento\":\"79123456\",\"fechaSalida\":\"2026-10-03T08:00\",\"fechaDevolucionReal\":\"2026-10-05T17:30\"}")));

		var command = unicoComando();
		assertEquals(TipoMovimiento.DEVOLUCION, command.tipoMovimiento());
		assertEquals(LocalDateTime.of(2026, 10, 5, 17, 30), command.fechaMovimiento());
	}

	@Test
	@DisplayName("ACTIVO_CREADO -> movimiento ALTA con occurredAt en hora local y el responsable")
	void activoCreado() {
		consumidor.consumir(registro("eter.activos.eventos.v1", evento(
				"33333333-3333-4333-8333-333333333333", "ACTIVO_CREADO",
				"{\"numDocumentoResponsable\":\"1075000111\"}")));

		var command = unicoComando();
		assertEquals(TipoMovimiento.ALTA, command.tipoMovimiento());
		assertEquals(OffsetDateTime.parse("2026-10-03T15:00:00.123-05:00")
				.atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime(), command.fechaMovimiento());
		assertEquals("1075000111", command.numDocumento());
	}

	@Test
	@DisplayName("PRESTAMO_VENCIDO no mueve inventario")
	void vencidoSeIgnora() {
		consumidor.consumir(registro("eter.prestamos.eventos.v1", evento(
				"44444444-4444-4444-8444-444444444444", "PRESTAMO_VENCIDO", "{\"numDocumento\":\"79123456\"}")));

		verify(useCase, never()).registrar(any());
	}

	@Test
	@DisplayName("el mismo evento entregado dos veces genera UN solo movimiento")
	void duplicado() {
		String mensaje = evento("55555555-5555-4555-8555-555555555555", "ACTIVO_DADO_DE_BAJA",
				"{\"numDocumentoResponsable\":\"79123456\"}");

		consumidor.consumir(registro("eter.activos.eventos.v1", mensaje));
		consumidor.consumir(registro("eter.activos.eventos.v1", mensaje));

		verify(useCase, times(1)).registrar(any());
	}

	@Test
	@DisplayName("sin eventId: mensaje invalido (va directo al DLT)")
	void sinEventId() {
		assertThrows(MensajeKafkaInvalidoException.class, () -> consumidor.consumir(
				registro("eter.prestamos.eventos.v1", "{\"eventType\":\"PRESTAMO_CREADO\"}")));
		verify(useCase, never()).registrar(any());
	}
}
