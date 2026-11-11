package com.eter.eterbackend.activos.application.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import org.springframework.transaction.annotation.Transactional;

import com.eter.eterbackend.activos.application.port.in.CrearPrestamoTemporalUseCase;
import com.eter.eterbackend.activos.application.port.in.GenerarAvisosPrestamosVencidosUseCase;
import com.eter.eterbackend.activos.application.port.in.ListarPrestamosUseCase;
import com.eter.eterbackend.activos.application.port.in.RegistrarDevolucionPrestamoUseCase;
import com.eter.eterbackend.activos.domain.exception.ActivoFijoNoEncontradoException;
import com.eter.eterbackend.activos.domain.exception.PrestamoNoEncontradoException;
import com.eter.eterbackend.activos.domain.exception.PrestamoNoVigenteException;
import com.eter.eterbackend.activos.domain.exception.PrestamoVigenteYaExisteException;
import com.eter.eterbackend.activos.domain.model.EstadoPrestamo;
import com.eter.eterbackend.activos.domain.model.PrestamoTemporal;
import com.eter.eterbackend.shared.activos.ActivoExistencePort;
import com.eter.eterbackend.activos.domain.repository.PrestamoTemporalRepository;
import com.eter.eterbackend.shared.events.domain.EventoOutbox;
import com.eter.eterbackend.shared.events.domain.EventoOutboxRepository;

/** Casos de uso del prestamo; datos y evento del Outbox en la misma transaccion. */
public class PrestamoTemporalService implements
		CrearPrestamoTemporalUseCase,
		ListarPrestamosUseCase,
		RegistrarDevolucionPrestamoUseCase,
		GenerarAvisosPrestamosVencidosUseCase {

	private final PrestamoTemporalRepository prestamoTemporalRepository;
	private final ActivoExistencePort activoFijoRepository;
	private final EventoOutboxRepository eventoOutboxRepository;

	public PrestamoTemporalService(PrestamoTemporalRepository prestamoTemporalRepository,
			ActivoExistencePort activoFijoRepository,
			EventoOutboxRepository eventoOutboxRepository) {
		this.prestamoTemporalRepository = prestamoTemporalRepository;
		this.activoFijoRepository = activoFijoRepository;
		this.eventoOutboxRepository = eventoOutboxRepository;
	}

	@Override
	@Transactional
	public PrestamoTemporal crear(CrearPrestamoTemporalUseCase.Command command) {
		Objects.requireNonNull(command, "El comando de creación no puede ser nulo.");

		if (!activoFijoRepository.existsById(command.nitUniv(), command.placaInventario())) {
			throw new ActivoFijoNoEncontradoException(
					"No existe el activo %s en la universidad %s."
							.formatted(command.placaInventario(), command.nitUniv()));
		}

		// Un solo prestamo vigente por activo (el indice unico cubre la carrera).
		if (prestamoTemporalRepository.existeVigente(command.nitUniv(), command.placaInventario())) {
			throw new PrestamoVigenteYaExisteException(
					"El activo %s ya tiene un préstamo vigente en la universidad %s."
							.formatted(command.placaInventario(), command.nitUniv()));
		}

		PrestamoTemporal prestamo = PrestamoTemporal.nuevo(
				command.nitUniv(), command.placaInventario(), command.numDocumento(),
				command.fechaSalida(), command.fechaDevolucionPactada());

		PrestamoTemporal guardado = prestamoTemporalRepository.save(prestamo);

		// Misma transaccion que el INSERT; lo consumen inventario y notificaciones.
		eventoOutboxRepository.guardarSiNoExiste(EventoOutbox.nuevo(
				"PRESTAMO_CREADO:" + guardado.idPrestamo(),
				guardado.nitUniv(), guardado.placaInventario(),
				"PRESTAMO_CREADO", guardado.numDocumento(),
				payloadPrestamo(guardado, "PRESTAMO_CREADO")));

		return guardado;
	}

	@Override
	public List<PrestamoTemporal> listar(String nitUniv, String placaInventario, EstadoPrestamo estado) {
		return prestamoTemporalRepository.buscar(nitUniv, placaInventario, estado);
	}

	@Override
	@Transactional
	public PrestamoTemporal registrar(RegistrarDevolucionPrestamoUseCase.Command command) {
		Objects.requireNonNull(command, "El comando de devolución no puede ser nulo.");

		PrestamoTemporal prestamo = prestamoTemporalRepository.findById(command.idPrestamo())
				.orElseThrow(() -> new PrestamoNoEncontradoException(
						"No existe el préstamo %s.".formatted(command.idPrestamo())));

		// Otra universidad: 404 para no revelar que existe.
		if (!prestamo.nitUniv().equals(command.nitUniv())) {
			throw new PrestamoNoEncontradoException(
					"No existe el préstamo %s.".formatted(command.idPrestamo()));
		}

		// 409 si ya fue devuelto o cancelado.
		if (!prestamo.estaVigente()) {
			throw new PrestamoNoVigenteException(
					"El préstamo %s ya está en estado %s."
							.formatted(prestamo.idPrestamo(), prestamo.estado()));
		}

		PrestamoTemporal devuelto = prestamo.registrarDevolucion(command.fechaDevolucionReal());
		PrestamoTemporal guardado = prestamoTemporalRepository.save(devuelto);

		// Misma transaccion que el UPDATE de arriba.
		eventoOutboxRepository.guardarSiNoExiste(EventoOutbox.nuevo(
				"PRESTAMO_DEVUELTO:" + guardado.idPrestamo(),
				guardado.nitUniv(), guardado.placaInventario(),
				"PRESTAMO_DEVUELTO", guardado.numDocumento(),
				payloadPrestamo(guardado, "PRESTAMO_DEVUELTO")));

		return guardado;
	}

	@Override
	@Transactional
	public int generarAvisos() {
		List<PrestamoTemporal> vencidos = prestamoTemporalRepository.findVigentesVencidos(LocalDateTime.now());
		int nuevos = 0;

		for (PrestamoTemporal prestamo : vencidos) {
			// El dedupKey evita repetir el aviso en cada vuelta del vigia.
			boolean registrado = eventoOutboxRepository.guardarSiNoExiste(EventoOutbox.nuevo(
					"PRESTAMO_VENCIDO:" + prestamo.idPrestamo(),
					prestamo.nitUniv(), prestamo.placaInventario(),
					"PRESTAMO_VENCIDO", "vigia-prestamos",
					payloadPrestamo(prestamo, "PRESTAMO_VENCIDO")));
			if (registrado) {
				nuevos++;
			}
		}
		return nuevos;
	}

	/** JSON a mano para no meter Jackson en la capa de aplicacion. */
	private String payloadPrestamo(PrestamoTemporal prestamo, String tipo) {
		return """
				{"tipo":"%s","idPrestamo":"%s","nitUniv":"%s","placaInventario":"%s",\
				"numDocumento":"%s","destinatarioEmail":"%s","assetKey":"%s","estado":"%s",\
				"fechaSalida":"%s","fechaDevolucionPactada":"%s","fechaDevolucionReal":%s}"""
				.formatted(tipo, prestamo.idPrestamo(), prestamo.nitUniv(), prestamo.placaInventario(),
						prestamo.numDocumento(), prestamo.numDocumento() + "@eter.edu.co",
						prestamo.assetKey(), prestamo.estado(), prestamo.fechaSalida(),
						prestamo.fechaDevolucionPactada(),
						prestamo.fechaDevolucionReal() == null ? "null"
								: "\"" + prestamo.fechaDevolucionReal() + "\"");
	}
}
