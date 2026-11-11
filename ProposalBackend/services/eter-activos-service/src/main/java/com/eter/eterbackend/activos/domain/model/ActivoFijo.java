package com.eter.eterbackend.activos.domain.model;

import java.math.BigDecimal;
import java.util.Objects;

import com.eter.eterbackend.activos.domain.exception.DatoInvalidoException;

/**
 * Activo fijo. Su identidad es la clave compuesta (nitUniv, placaInventario):
 * cada universidad maneja su propio espacio de placas.
 */
public record ActivoFijo(
		String nitUniv,
		String placaInventario,
		String descripcion,
		String codUnspsc,
		String codSede,
		BigDecimal valorContable,
		EstadoActivo estado
) {

	public ActivoFijo {
		nitUniv = ValidacionCampos.obligatorio(nitUniv, "nitUniv", 20);
		placaInventario = ValidacionCampos.obligatorio(placaInventario, "placaInventario", 50);
		descripcion = ValidacionCampos.obligatorio(descripcion, "descripcion", 255);
		codUnspsc = ValidacionCampos.obligatorio(codUnspsc, "codUnspsc", 50);
		codSede = ValidacionCampos.obligatorio(codSede, "codSede", 20);
		Objects.requireNonNull(valorContable, "El campo valorContable es obligatorio.");
		if (valorContable.signum() < 0) {
			throw new DatoInvalidoException("El campo valorContable no puede ser negativo.");
		}
		Objects.requireNonNull(estado, "El campo estado es obligatorio.");
	}

	/** Identidad estable del activo que se usa como key de Kafka. */
	public String assetKey() {
		return nitUniv + ":" + placaInventario;
	}

	/** Baja logica: conserva el registro y solo cambia el estado. */
	public ActivoFijo darDeBaja() {
		return new ActivoFijo(nitUniv, placaInventario, descripcion, codUnspsc, codSede,
				valorContable, EstadoActivo.BAJA);
	}
}
