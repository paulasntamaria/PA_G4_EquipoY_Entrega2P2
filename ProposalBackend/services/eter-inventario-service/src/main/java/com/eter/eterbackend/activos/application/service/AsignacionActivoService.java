package com.eter.eterbackend.activos.application.service;

import java.util.List;
import java.util.Objects;

import com.eter.eterbackend.activos.application.port.in.CrearAsignacionActivoUseCase;
import com.eter.eterbackend.activos.application.port.in.ListarAsignacionesUseCase;
import com.eter.eterbackend.activos.domain.exception.ActivoFijoNoEncontradoException;
import com.eter.eterbackend.activos.domain.exception.AsignacionActivoYaExisteException;
import com.eter.eterbackend.activos.domain.model.AsignacionActivo;
import com.eter.eterbackend.shared.activos.ActivoExistencePort;
import com.eter.eterbackend.activos.domain.repository.AsignacionActivoRepository;

public class AsignacionActivoService implements CrearAsignacionActivoUseCase, ListarAsignacionesUseCase {

	private final AsignacionActivoRepository asignacionActivoRepository;
	private final ActivoExistencePort activoFijoRepository;

	public AsignacionActivoService(AsignacionActivoRepository asignacionActivoRepository,
			ActivoExistencePort activoFijoRepository) {
		this.asignacionActivoRepository = asignacionActivoRepository;
		this.activoFijoRepository = activoFijoRepository;
	}

	@Override
	public AsignacionActivo crear(CrearAsignacionActivoUseCase.Command command) {
		Objects.requireNonNull(command, "El comando de creación no puede ser nulo.");

		if (!activoFijoRepository.existsById(command.nitUniv(), command.placaInventario())) {
			throw new ActivoFijoNoEncontradoException(
					"No existe el activo %s en la universidad %s."
							.formatted(command.placaInventario(), command.nitUniv()));
		}
		if (asignacionActivoRepository.existsById(command.nitUniv(), command.placaInventario(),
				command.numDocumento())) {
			throw new AsignacionActivoYaExisteException(
					"El activo %s ya está asignado al documento %s."
							.formatted(command.placaInventario(), command.numDocumento()));
		}

		return asignacionActivoRepository.save(new AsignacionActivo(
				command.nitUniv(), command.placaInventario(),
				command.numDocumento(), command.fechaHoraActa()));
	}

	@Override
	public List<AsignacionActivo> listar(String nitUniv, String placaInventario, String numDocumento) {
		return asignacionActivoRepository.buscar(nitUniv, placaInventario, numDocumento);
	}
}
