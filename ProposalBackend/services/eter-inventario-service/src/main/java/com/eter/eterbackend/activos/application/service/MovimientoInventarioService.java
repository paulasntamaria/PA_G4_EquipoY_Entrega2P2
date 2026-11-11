package com.eter.eterbackend.activos.application.service;

import java.util.List;
import java.util.Objects;

import com.eter.eterbackend.activos.application.port.in.CrearMovimientoInventarioUseCase;
import com.eter.eterbackend.activos.application.port.in.ListarMovimientosUseCase;
import com.eter.eterbackend.activos.application.port.in.RegistrarMovimientoDesdeEventoUseCase;
import com.eter.eterbackend.activos.domain.exception.ActivoFijoNoEncontradoException;
import com.eter.eterbackend.activos.domain.model.MovimientoInventario;
import com.eter.eterbackend.activos.domain.model.TipoMovimiento;
import com.eter.eterbackend.shared.activos.ActivoExistencePort;
import com.eter.eterbackend.activos.domain.repository.MovimientoInventarioRepository;

public class MovimientoInventarioService implements CrearMovimientoInventarioUseCase, ListarMovimientosUseCase,
		RegistrarMovimientoDesdeEventoUseCase {

	/** Prefijo de la clave de idempotencia de los movimientos que vienen de eventos. */
	public static final String CLAVE_EVENTO = "evt:";

	private final MovimientoInventarioRepository movimientoInventarioRepository;
	private final ActivoExistencePort activoFijoRepository;

	public MovimientoInventarioService(MovimientoInventarioRepository movimientoInventarioRepository,
			ActivoExistencePort activoFijoRepository) {
		this.movimientoInventarioRepository = movimientoInventarioRepository;
		this.activoFijoRepository = activoFijoRepository;
	}

	@Override
	public MovimientoInventario crear(CrearMovimientoInventarioUseCase.Command command) {
		Objects.requireNonNull(command, "El comando de creación no puede ser nulo.");

		// Misma Idempotency-Key: se devuelve el movimiento original.
		String clave = command.claveIdempotencia();
		if (clave != null) {
			var existente = movimientoInventarioRepository.findByClaveIdempotencia(command.nitUniv(), clave);
			if (existente.isPresent()) {
				return existente.get();
			}
		}

		if (!activoFijoRepository.existsById(command.nitUniv(), command.placaInventario())) {
			throw new ActivoFijoNoEncontradoException(
					"No existe el activo %s en la universidad %s."
							.formatted(command.placaInventario(), command.nitUniv()));
		}

		return movimientoInventarioRepository.save(new MovimientoInventario(
				null, command.nitUniv(), command.placaInventario(),
				command.tipoMovimiento(), command.fechaMovimiento(), command.numDocumento()), clave);
	}

	/** Movimiento desde un evento; la clave "evt:" + eventId evita duplicados. */
	@Override
	public boolean registrar(RegistrarMovimientoDesdeEventoUseCase.Command command) {
		Objects.requireNonNull(command, "El comando del evento no puede ser nulo.");

		String clave = CLAVE_EVENTO + command.eventId();
		if (movimientoInventarioRepository.findByClaveIdempotencia(command.nitUniv(), clave).isPresent()) {
			return false;
		}

		movimientoInventarioRepository.save(new MovimientoInventario(
				null, command.nitUniv(), command.placaInventario(),
				command.tipoMovimiento(), command.fechaMovimiento(), command.numDocumento()), clave);
		return true;
	}

	@Override
	public List<MovimientoInventario> listar(String nitUniv, String placaInventario, TipoMovimiento tipoMovimiento) {
		return movimientoInventarioRepository.buscar(nitUniv, placaInventario, tipoMovimiento);
	}
}
