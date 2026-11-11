package com.eter.eterbackend.activos.application.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.eter.eterbackend.activos.application.port.in.RegistrarMovimientoDesdeEventoUseCase;
import com.eter.eterbackend.activos.domain.model.MovimientoInventario;
import com.eter.eterbackend.activos.domain.model.TipoMovimiento;
import com.eter.eterbackend.activos.domain.repository.MovimientoInventarioRepository;
import com.eter.eterbackend.shared.activos.ActivoExistencePort;

@DisplayName("MovimientoInventarioService.registrar (movimiento desde evento)")
class MovimientoDesdeEventoTest {

	private static final String EVENT_ID = "11111111-1111-4111-8111-111111111111";
	private static final String CLAVE = "evt:" + EVENT_ID;

	private MovimientoInventarioRepository repositorio;
	private ActivoExistencePort activos;
	private MovimientoInventarioService servicio;

	private final RegistrarMovimientoDesdeEventoUseCase.Command command = new RegistrarMovimientoDesdeEventoUseCase.Command(
			EVENT_ID, "900123456-1", "AF-2026-0001", TipoMovimiento.PRESTAMO,
			LocalDateTime.of(2026, 10, 3, 8, 0), "79123456");

	@BeforeEach
	void preparar() {
		repositorio = mock(MovimientoInventarioRepository.class);
		activos = mock(ActivoExistencePort.class);
		servicio = new MovimientoInventarioService(repositorio, activos);
	}

	@Test
	@DisplayName("primera vez: guarda el movimiento con la clave evt:{eventId} y no llama a activos-service")
	void primeraVez() {
		when(repositorio.findByClaveIdempotencia("900123456-1", CLAVE)).thenReturn(Optional.empty());

		assertTrue(servicio.registrar(command));

		verify(repositorio).save(any(MovimientoInventario.class), eq(CLAVE));
		verifyNoInteractions(activos);
	}

	@Test
	@DisplayName("evento repetido: no guarda un segundo movimiento")
	void repetido() {
		when(repositorio.findByClaveIdempotencia("900123456-1", CLAVE)).thenReturn(Optional.of(
				new MovimientoInventario(1L, "900123456-1", "AF-2026-0001", TipoMovimiento.PRESTAMO,
						LocalDateTime.of(2026, 10, 3, 8, 0), "79123456")));

		assertFalse(servicio.registrar(command));

		verify(repositorio, never()).save(any(), any());
	}
}
