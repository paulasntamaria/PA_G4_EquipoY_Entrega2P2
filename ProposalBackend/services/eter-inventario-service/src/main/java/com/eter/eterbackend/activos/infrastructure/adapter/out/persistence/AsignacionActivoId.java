package com.eter.eterbackend.activos.infrastructure.adapter.out.persistence;

import java.io.Serializable;
import java.util.Objects;

public class AsignacionActivoId implements Serializable {

	private String nitUniv;
	private String placaInventario;
	private String numDocumento;

	public AsignacionActivoId() {
	}

	public AsignacionActivoId(String nitUniv, String placaInventario, String numDocumento) {
		this.nitUniv = nitUniv;
		this.placaInventario = placaInventario;
		this.numDocumento = numDocumento;
	}

	@Override
	public boolean equals(Object otro) {
		if (this == otro) {
			return true;
		}
		if (!(otro instanceof AsignacionActivoId id)) {
			return false;
		}
		return Objects.equals(nitUniv, id.nitUniv)
				&& Objects.equals(placaInventario, id.placaInventario)
				&& Objects.equals(numDocumento, id.numDocumento);
	}

	@Override
	public int hashCode() {
		return Objects.hash(nitUniv, placaInventario, numDocumento);
	}
}
