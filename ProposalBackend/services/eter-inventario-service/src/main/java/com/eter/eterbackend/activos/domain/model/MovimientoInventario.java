package com.eter.eterbackend.activos.domain.model;

import java.time.LocalDateTime;
import java.util.Objects;

/** Movimiento de inventario (solo se registra, no se edita). */
public record MovimientoInventario(
		Long idMovimiento,
		String nitUniv,
		String placaInventario,
		TipoMovimiento tipoMovimiento,
		LocalDateTime fechaMovimiento,
		String numDocumento
) {

	public MovimientoInventario {
		nitUniv = ValidacionCampos.obligatorio(nitUniv, "nitUniv", 20);
		placaInventario = ValidacionCampos.obligatorio(placaInventario, "placaInventario", 50);
		numDocumento = ValidacionCampos.obligatorio(numDocumento, "numDocumento", 20);
		Objects.requireNonNull(tipoMovimiento, "El campo tipoMovimiento es obligatorio.");
		Objects.requireNonNull(fechaMovimiento, "El campo fechaMovimiento es obligatorio.");
	}
}
