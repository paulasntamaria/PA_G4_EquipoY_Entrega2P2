package com.eter.eterbackend.activos.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.eter.eterbackend.activos.application.port.in.CrearActivoFijoUseCase;
import com.eter.eterbackend.activos.application.port.out.ActorActualPort;
import com.eter.eterbackend.activos.domain.model.ActivoFijo;
import com.eter.eterbackend.activos.domain.model.EstadoActivo;
import com.eter.eterbackend.activos.domain.repository.ActivoFijoRepository;
import com.eter.eterbackend.shared.events.domain.EventoOutbox;
import com.eter.eterbackend.shared.events.domain.EventoOutboxRepository;

/** El alta y la baja del activo dejan su evento en el Outbox para inventario. */
@DisplayName("ActivoFijoService -> eventos en el Outbox")
class ActivoEventosOutboxTest {

	private static final String NIT = "900123456-1";
	private static final String PLACA = "AF-2026-0100";

	private ActivoFijoRepository activos;
	private EventoOutboxRepository outbox;
	private ActivoFijoService servicio;

	@BeforeEach
	void preparar() {
		activos = mock(ActivoFijoRepository.class);
		outbox = mock(EventoOutboxRepository.class);
		ActorActualPort actor = () -> "79123456@eter.edu.co";
		when(activos.save(any())).thenAnswer(inv -> inv.getArgument(0));
		servicio = new ActivoFijoService(activos, outbox, actor);
	}

	private static ActivoFijo activo(EstadoActivo estado) {
		return new ActivoFijo(NIT, PLACA, "Monitor \"Dell\" 27", "43211902", "SED-01",
				new BigDecimal("1850000"), estado);
	}

	@Test
	@DisplayName("crear un activo registra ACTIVO_CREADO con el documento del responsable")
	void crearEmiteActivoCreado() {
		servicio.crear(new CrearActivoFijoUseCase.Command(NIT, PLACA, "Monitor \"Dell\" 27", "43211902",
				"SED-01", new BigDecimal("1850000"), EstadoActivo.ACTIVO));

		var captor = ArgumentCaptor.forClass(EventoOutbox.class);
		verify(outbox).guardarSiNoExiste(captor.capture());
		EventoOutbox evento = captor.getValue();
		assertEquals("ACTIVO_CREADO", evento.eventType());
		assertEquals(NIT + ":" + PLACA, evento.aggregateKey());
		assertEquals("79123456@eter.edu.co", evento.actor());
		assertTrue(evento.payload().contains("\"numDocumentoResponsable\":\"79123456\""));
		assertTrue(evento.payload().contains("Monitor \\\"Dell\\\" 27"), "Las comillas deben ir escapadas");
	}

	@Test
	@DisplayName("dar de baja registra ACTIVO_DADO_DE_BAJA")
	void bajaEmiteEvento() {
		when(activos.findById(NIT, PLACA)).thenReturn(Optional.of(activo(EstadoActivo.ACTIVO)));

		servicio.darDeBaja(NIT, PLACA);

		var captor = ArgumentCaptor.forClass(EventoOutbox.class);
		verify(outbox).guardarSiNoExiste(captor.capture());
		assertEquals("ACTIVO_DADO_DE_BAJA", captor.getValue().eventType());
	}

	@Test
	@DisplayName("repetir la baja de un activo que ya esta en BAJA no genera otro evento")
	void bajaRepetidaNoEmite() {
		when(activos.findById(NIT, PLACA)).thenReturn(Optional.of(activo(EstadoActivo.BAJA)));

		servicio.darDeBaja(NIT, PLACA);

		verify(outbox, never()).guardarSiNoExiste(any());
	}
}
