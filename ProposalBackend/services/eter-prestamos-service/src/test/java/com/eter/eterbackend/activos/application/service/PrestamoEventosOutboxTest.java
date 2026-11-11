package com.eter.eterbackend.activos.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.eter.eterbackend.activos.application.port.in.CrearPrestamoTemporalUseCase;
import com.eter.eterbackend.activos.application.port.in.RegistrarDevolucionPrestamoUseCase;
import com.eter.eterbackend.activos.domain.model.EstadoPrestamo;
import com.eter.eterbackend.activos.domain.model.PrestamoTemporal;
import com.eter.eterbackend.activos.domain.repository.PrestamoTemporalRepository;
import com.eter.eterbackend.shared.activos.ActivoExistencePort;
import com.eter.eterbackend.shared.events.domain.EventoOutbox;
import com.eter.eterbackend.shared.events.domain.EventoOutboxRepository;

/**
 * Comprueba que cada caso de uso deja en el Outbox el evento que luego
 * consumen inventario y notificaciones, con el formato documentado.
 */
@DisplayName("PrestamoTemporalService -> eventos en el Outbox")
class PrestamoEventosOutboxTest {

	private static final String NIT = "900123456-1";
	private static final String PLACA = "AF-2026-0001";
	private static final UUID ID = UUID.fromString("7c9e6679-7425-40de-944b-e07fc1f90ae7");

	private PrestamoTemporalRepository prestamos;
	private EventoOutboxRepository outbox;
	private PrestamoTemporalService servicio;

	@BeforeEach
	void preparar() {
		prestamos = mock(PrestamoTemporalRepository.class);
		outbox = mock(EventoOutboxRepository.class);
		ActivoExistencePort activos = mock(ActivoExistencePort.class);
		when(activos.existsById(NIT, PLACA)).thenReturn(true);
		when(outbox.guardarSiNoExiste(any())).thenReturn(true);
		servicio = new PrestamoTemporalService(prestamos, activos, outbox);
	}

	private EventoOutbox eventoGuardado() {
		var captor = ArgumentCaptor.forClass(EventoOutbox.class);
		verify(outbox).guardarSiNoExiste(captor.capture());
		return captor.getValue();
	}

	@Test
	@DisplayName("crear un prestamo registra PRESTAMO_CREADO con key nitUniv:placa")
	void crearEmitePrestamoCreado() {
		LocalDateTime salida = LocalDateTime.of(2026, 10, 3, 8, 0);
		when(prestamos.save(any())).thenAnswer(inv -> {
			PrestamoTemporal p = inv.getArgument(0);
			return new PrestamoTemporal(ID, p.nitUniv(), p.placaInventario(), p.numDocumento(),
					p.fechaSalida(), p.fechaDevolucionPactada(), null, EstadoPrestamo.VIGENTE);
		});

		servicio.crear(new CrearPrestamoTemporalUseCase.Command(NIT, PLACA, "79123456", salida, salida.plusDays(7)));

		EventoOutbox evento = eventoGuardado();
		assertEquals("PRESTAMO_CREADO", evento.eventType());
		assertEquals("PRESTAMO_CREADO:" + ID, evento.dedupKey());
		assertEquals(NIT + ":" + PLACA, evento.aggregateKey());
		assertTrue(evento.payload().contains("\"fechaSalida\":\"2026-10-03T08:00\""));
		assertTrue(evento.payload().contains("\"fechaDevolucionReal\":null"));
	}

	@Test
	@DisplayName("registrar la devolucion registra PRESTAMO_DEVUELTO con la fecha real")
	void devolucionEmitePrestamoDevuelto() {
		LocalDateTime salida = LocalDateTime.of(2026, 10, 1, 8, 0);
		when(prestamos.findById(ID)).thenReturn(Optional.of(new PrestamoTemporal(ID, NIT, PLACA, "79123456",
				salida, salida.plusDays(7), null, EstadoPrestamo.VIGENTE)));
		when(prestamos.save(any())).thenAnswer(inv -> inv.getArgument(0));

		servicio.registrar(new RegistrarDevolucionPrestamoUseCase.Command(ID, NIT, LocalDateTime.of(2026, 10, 3, 17, 30)));

		EventoOutbox evento = eventoGuardado();
		assertEquals("PRESTAMO_DEVUELTO", evento.eventType());
		assertTrue(evento.payload().contains("\"fechaDevolucionReal\":\"2026-10-03T17:30\""));
	}
}
