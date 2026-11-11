package com.eter.eterbackend.activos.domain.model;

import java.time.LocalDateTime;
import java.util.Objects;

/** Asignacion de custodia de un activo a un empleado. */
public record AsignacionActivo(
		String nitUniv,
		String placaInventario,
		String numDocumento,
		LocalDateTime fechaHoraActa
) {

	public AsignacionActivo {
		nitUniv = ValidacionCampos.obligatorio(nitUniv, "nitUniv", 20);
		placaInventario = ValidacionCampos.obligatorio(placaInventario, "placaInventario", 50);
		numDocumento = ValidacionCampos.obligatorio(numDocumento, "numDocumento", 20);
		Objects.requireNonNull(fechaHoraActa, "El campo fechaHoraActa es obligatorio.");
	}
}
