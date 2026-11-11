package com.eter.eterbackend.activos.infrastructure.adapter.out.persistence;

import java.io.Serializable;
import java.util.Objects;

/** Clave compuesta de activo_fijo: (nit_univ, placa_inventario). */
public class ActivoFijoId implements Serializable {

	private String nitUniv;
	private String placaInventario;

	public ActivoFijoId() {
	}

	public ActivoFijoId(String nitUniv, String placaInventario) {
		this.nitUniv = nitUniv;
		this.placaInventario = placaInventario;
	}

	public String getNitUniv() {
		return nitUniv;
	}

	public String getPlacaInventario() {
		return placaInventario;
	}

	@Override
	public boolean equals(Object otro) {
		if (this == otro) {
			return true;
		}
		if (!(otro instanceof ActivoFijoId id)) {
			return false;
		}
		return Objects.equals(nitUniv, id.nitUniv) && Objects.equals(placaInventario, id.placaInventario);
	}

	@Override
	public int hashCode() {
		return Objects.hash(nitUniv, placaInventario);
	}
}
