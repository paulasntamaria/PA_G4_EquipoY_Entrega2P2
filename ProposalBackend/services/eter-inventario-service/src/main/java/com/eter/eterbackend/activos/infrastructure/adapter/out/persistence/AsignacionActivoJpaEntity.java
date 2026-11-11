package com.eter.eterbackend.activos.infrastructure.adapter.out.persistence;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

@Entity
@Table(name = "asignacion_activo")
@IdClass(AsignacionActivoId.class)
public class AsignacionActivoJpaEntity {

	@Id
	@Column(name = "nit_univ", length = 20, nullable = false)
	private String nitUniv;

	@Id
	@Column(name = "placa_inventario", length = 50, nullable = false)
	private String placaInventario;

	@Id
	@Column(name = "num_documento", length = 20, nullable = false)
	private String numDocumento;

	@Column(name = "fecha_hora_acta", nullable = false)
	private LocalDateTime fechaHoraActa;

	protected AsignacionActivoJpaEntity() {
	}

	public AsignacionActivoJpaEntity(String nitUniv, String placaInventario, String numDocumento,
			LocalDateTime fechaHoraActa) {
		this.nitUniv = nitUniv;
		this.placaInventario = placaInventario;
		this.numDocumento = numDocumento;
		this.fechaHoraActa = fechaHoraActa;
	}

	public String getNitUniv() { return nitUniv; }
	public String getPlacaInventario() { return placaInventario; }
	public String getNumDocumento() { return numDocumento; }
	public LocalDateTime getFechaHoraActa() { return fechaHoraActa; }
}
