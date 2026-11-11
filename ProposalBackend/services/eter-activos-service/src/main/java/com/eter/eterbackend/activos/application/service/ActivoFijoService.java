package com.eter.eterbackend.activos.application.service;

import java.util.List;
import java.util.Objects;

import org.springframework.transaction.annotation.Transactional;

import com.eter.eterbackend.activos.application.port.in.ActualizarActivoFijoUseCase;
import com.eter.eterbackend.activos.application.port.in.CrearActivoFijoUseCase;
import com.eter.eterbackend.activos.application.port.in.DarDeBajaActivoFijoUseCase;
import com.eter.eterbackend.activos.application.port.in.ListarActivosFijosUseCase;
import com.eter.eterbackend.activos.application.port.in.ObtenerActivoFijoUseCase;
import com.eter.eterbackend.activos.application.port.out.ActorActualPort;
import com.eter.eterbackend.activos.domain.exception.ActivoFijoNoEncontradoException;
import com.eter.eterbackend.activos.domain.exception.ActivoFijoYaExisteException;
import com.eter.eterbackend.activos.domain.model.ActivoFijo;
import com.eter.eterbackend.activos.domain.model.EstadoActivo;
import com.eter.eterbackend.activos.domain.repository.ActivoFijoRepository;
import com.eter.eterbackend.shared.events.domain.EventoOutbox;
import com.eter.eterbackend.shared.events.domain.EventoOutboxRepository;

/**
 * Casos de uso del activo fijo. Es una clase Java normal: no tiene @Service ni
 * ninguna anotacion de Spring. El cableado se hace en ActivoFijoConfig, que es
 * lo que mantiene la capa de aplicacion independiente del framework.
 *
 * crear y darDeBaja son @Transactional porque, ademas del cambio en
 * activo_fijo, dejan su evento en el Outbox (ACTIVO_CREADO / ACTIVO_DADO_DE_BAJA).
 * Se guardan los dos o ninguno.
 */
public class ActivoFijoService implements
		CrearActivoFijoUseCase,
		ActualizarActivoFijoUseCase,
		ObtenerActivoFijoUseCase,
		ListarActivosFijosUseCase,
		DarDeBajaActivoFijoUseCase {

	private final ActivoFijoRepository activoFijoRepository;
	private final EventoOutboxRepository eventoOutboxRepository;
	private final ActorActualPort actorActual;

	public ActivoFijoService(ActivoFijoRepository activoFijoRepository,
			EventoOutboxRepository eventoOutboxRepository,
			ActorActualPort actorActual) {
		this.activoFijoRepository = activoFijoRepository;
		this.eventoOutboxRepository = eventoOutboxRepository;
		this.actorActual = actorActual;
	}

	@Override
	@Transactional
	public ActivoFijo crear(CrearActivoFijoUseCase.Command command) {
		Objects.requireNonNull(command, "El comando de creación no puede ser nulo.");

		if (activoFijoRepository.existsById(command.nitUniv(), command.placaInventario())) {
			throw new ActivoFijoYaExisteException(
					"Ya existe un activo con placa %s en la universidad %s."
							.formatted(command.placaInventario(), command.nitUniv()));
		}

		ActivoFijo activoFijo = new ActivoFijo(
				command.nitUniv(), command.placaInventario(), command.descripcion(),
				command.codUnspsc(), command.codSede(), command.valorContable(), command.estado());

		ActivoFijo guardado = activoFijoRepository.save(activoFijo);
		registrarEvento("ACTIVO_CREADO", guardado);
		return guardado;
	}

	@Override
	public ActivoFijo actualizar(ActualizarActivoFijoUseCase.Command command) {
		Objects.requireNonNull(command, "El comando de actualización no puede ser nulo.");

		// Se comprueba que exista antes de escribir: el PUT no crea recursos nuevos.
		buscarObligatorio(command.nitUniv(), command.placaInventario());

		ActivoFijo actualizado = new ActivoFijo(
				command.nitUniv(), command.placaInventario(), command.descripcion(),
				command.codUnspsc(), command.codSede(), command.valorContable(), command.estado());

		return activoFijoRepository.save(actualizado);
	}

	@Override
	public ActivoFijo obtener(String nitUniv, String placaInventario) {
		return buscarObligatorio(nitUniv, placaInventario);
	}

	@Override
	public List<ActivoFijo> listar(String nitUniv) {
		return activoFijoRepository.findByNitUniv(nitUniv);
	}

	@Override
	@Transactional
	public void darDeBaja(String nitUniv, String placaInventario) {
		ActivoFijo activoFijo = buscarObligatorio(nitUniv, placaInventario);
		boolean yaEstabaDeBaja = activoFijo.estado() == EstadoActivo.BAJA;
		ActivoFijo deBaja = activoFijoRepository.save(activoFijo.darDeBaja());

		// Repetir el DELETE no debe generar otra BAJA en el kardex.
		if (!yaEstabaDeBaja) {
			registrarEvento("ACTIVO_DADO_DE_BAJA", deBaja);
		}
	}

	private ActivoFijo buscarObligatorio(String nitUniv, String placaInventario) {
		return activoFijoRepository.findById(nitUniv, placaInventario)
				.orElseThrow(() -> new ActivoFijoNoEncontradoException(
						"No existe el activo %s en la universidad %s."
								.formatted(placaInventario, nitUniv)));
	}

	/**
	 * El dedupKey lleva un sello de tiempo para que un activo que se da de baja,
	 * se reactiva por PUT y se vuelve a dar de baja produzca dos eventos distintos.
	 */
	private void registrarEvento(String tipo, ActivoFijo activo) {
		eventoOutboxRepository.guardarSiNoExiste(EventoOutbox.nuevo(
				tipo + ":" + activo.assetKey() + ":" + System.nanoTime(),
				activo.nitUniv(), activo.placaInventario(), tipo, actorActual.email(),
				payloadActivo(tipo, activo)));
	}

	/** JSON a mano para no meter Jackson en la capa de aplicacion. */
	private String payloadActivo(String tipo, ActivoFijo activo) {
		return """
				{"tipo":"%s","nitUniv":"%s","placaInventario":"%s","assetKey":"%s",\
				"descripcion":"%s","codUnspsc":"%s","codSede":"%s","valorContable":%s,\
				"estado":"%s","numDocumentoResponsable":"%s"}"""
				.formatted(tipo, activo.nitUniv(), activo.placaInventario(), activo.assetKey(),
						escapar(activo.descripcion()), activo.codUnspsc(), activo.codSede(),
						activo.valorContable().toPlainString(), activo.estado(), actorActual.numDocumento());
	}

	private static String escapar(String texto) {
		return texto.replace("\\", "\\\\").replace("\"", "\\\"")
				.replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
	}
}
