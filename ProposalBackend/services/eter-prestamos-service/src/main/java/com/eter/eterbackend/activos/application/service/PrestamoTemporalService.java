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

/**
 * Casos de uso del prestamo temporal.
 *
 * Lo importante de esta clase es el @Transactional de crear, registrarDevolucion
 * y generarAvisos: el cambio de datos y la fila del Outbox se guardan juntos o no
 * se guarda ninguno. Ese es el patron Transactional Outbox y es lo que evita
 * que la base de datos y Kafka queden contando historias distintas.
 */
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

		// Idempotencia del POST: si el cliente reintenta, no se crea un segundo
		// prestamo del mismo activo. Si dos peticiones pasan este chequeo a la vez,
		// el indice uq_prestamo_vigente_por_activo rechaza la segunda (409).
		if (prestamoTemporalRepository.existeVigente(command.nitUniv(), command.placaInventario())) {
			throw new PrestamoVigenteYaExisteException(
					"El activo %s ya tiene un préstamo vigente en la universidad %s."
							.formatted(command.placaInventario(), command.nitUniv()));
		}

		PrestamoTemporal prestamo = PrestamoTemporal.nuevo(
				command.nitUniv(), command.placaInventario(), command.numDocumento(),
				command.fechaSalida(), command.fechaDevolucionPactada());

		PrestamoTemporal guardado = prestamoTemporalRepository.save(prestamo);

		// Misma transaccion que el INSERT: inventario lo consume para registrar el
		// movimiento PRESTAMO en el kardex y notificaciones avisa al custodio.
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

		// Un prestamo de otra universidad se trata como inexistente (404), para no
		// revelar que existe. Mismo criterio que usa notificaciones.
		if (!prestamo.nitUniv().equals(command.nitUniv())) {
			throw new PrestamoNoEncontradoException(
					"No existe el préstamo %s.".formatted(command.idPrestamo()));
		}

		// Regla del contrato: responder 409 si ya fue devuelto o cancelado.
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
			// El dedupKey lleva el id del prestamo, asi que aunque el vigia corra
			// cada minuto el evento se registra una sola vez.
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

	/**
	 * Se arma el JSON a mano para no meter Jackson en la capa de aplicacion.
	 * destinatarioEmail se deriva del documento del custodio: en el sistema real
	 * sale de empleado.correo_institucional, pero ese contexto (RRHH) no hace
	 * parte de este microservicio.
	 */
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
