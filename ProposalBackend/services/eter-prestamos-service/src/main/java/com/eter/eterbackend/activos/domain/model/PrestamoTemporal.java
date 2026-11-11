package com.eter.eterbackend.activos.domain.model;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

import com.eter.eterbackend.activos.domain.exception.DatoInvalidoException;

/**
 * Prestamo temporal de un activo.
 * Aqui vive la regla que resolvio el riesgo de la Entrega 1: un prestamo
 * solo se puede devolver una vez, y "vencido" se calcula, no se guarda.
 */
public record PrestamoTemporal(
		UUID idPrestamo,
		String nitUniv,
		String placaInventario,
		String numDocumento,
		LocalDateTime fechaSalida,
		LocalDateTime fechaDevolucionPactada,
		LocalDateTime fechaDevolucionReal,
		EstadoPrestamo estado
) {

	public PrestamoTemporal {
		nitUniv = ValidacionCampos.obligatorio(nitUniv, "nitUniv", 20);
		placaInventario = ValidacionCampos.obligatorio(placaInventario, "placaInventario", 50);
		numDocumento = ValidacionCampos.obligatorio(numDocumento, "numDocumento", 20);
		Objects.requireNonNull(fechaSalida, "El campo fechaSalida es obligatorio.");
		Objects.requireNonNull(fechaDevolucionPactada, "El campo fechaDevolucionPactada es obligatorio.");
		Objects.requireNonNull(estado, "El campo estado es obligatorio.");

		if (!fechaDevolucionPactada.isAfter(fechaSalida)) {
			throw new DatoInvalidoException(
					"La fechaDevolucionPactada debe ser posterior a la fechaSalida.");
		}
		if (estado == EstadoPrestamo.DEVUELTO && fechaDevolucionReal == null) {
			throw new DatoInvalidoException(
					"Un préstamo DEVUELTO debe tener fechaDevolucionReal.");
		}
	}

	/** Prestamo recien creado: siempre nace VIGENTE y sin devolucion real. */
	public static PrestamoTemporal nuevo(String nitUniv, String placaInventario, String numDocumento,
			LocalDateTime fechaSalida, LocalDateTime fechaDevolucionPactada) {
		return new PrestamoTemporal(null, nitUniv, placaInventario, numDocumento,
				fechaSalida, fechaDevolucionPactada, null, EstadoPrestamo.VIGENTE);
	}

	/** Derivado del contrato: true si sigue VIGENTE y la fecha pactada ya paso. */
	public boolean vencido() {
		return estado == EstadoPrestamo.VIGENTE && fechaDevolucionPactada.isBefore(LocalDateTime.now());
	}

	public boolean estaVigente() {
		return estado == EstadoPrestamo.VIGENTE;
	}

	public String assetKey() {
		return nitUniv + ":" + placaInventario;
	}

	/** Regla de negocio: solo un prestamo VIGENTE se puede devolver. */
	public PrestamoTemporal registrarDevolucion(LocalDateTime fechaDevolucionReal) {
		Objects.requireNonNull(fechaDevolucionReal, "El campo fechaDevolucionReal es obligatorio.");
		if (fechaDevolucionReal.isBefore(fechaSalida)) {
			throw new DatoInvalidoException(
					"La fechaDevolucionReal no puede ser anterior a la fechaSalida.");
		}
		return new PrestamoTemporal(idPrestamo, nitUniv, placaInventario, numDocumento,
				fechaSalida, fechaDevolucionPactada, fechaDevolucionReal, EstadoPrestamo.DEVUELTO);
	}
}
