package com.eter.eterbackend.activos.infrastructure.adapter.out.persistence;

import java.time.LocalDateTime;

import com.eter.eterbackend.activos.domain.model.TipoMovimiento;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "movimiento_inventario")
public class MovimientoInventarioJpaEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id_movimiento", nullable = false, updatable = false)
	private Long idMovimiento;

	@Column(name = "nit_univ", length = 20, nullable = false)
	private String nitUniv;

	@Column(name = "placa_inventario", length = 50, nullable = false)
	private String placaInventario;

	@Enumerated(EnumType.STRING)
	@Column(name = "tipo_movimiento", length = 50, nullable = false)
	private TipoMovimiento tipoMovimiento;

	@Column(name = "fecha_movimiento", nullable = false)
	private LocalDateTime fechaMovimiento;

	@Column(name = "num_documento", length = 20, nullable = false)
	private String numDocumento;

	@Column(name = "idempotency_key", length = 100, updatable = false)
	private String idempotencyKey;

	protected MovimientoInventarioJpaEntity() {
	}

	public MovimientoInventarioJpaEntity(Long idMovimiento, String nitUniv, String placaInventario,
			TipoMovimiento tipoMovimiento, LocalDateTime fechaMovimiento, String numDocumento,
			String idempotencyKey) {
		this.idMovimiento = idMovimiento;
		this.nitUniv = nitUniv;
		this.placaInventario = placaInventario;
		this.tipoMovimiento = tipoMovimiento;
		this.fechaMovimiento = fechaMovimiento;
		this.numDocumento = numDocumento;
		this.idempotencyKey = idempotencyKey;
	}

	public Long getIdMovimiento() { return idMovimiento; }
	public String getNitUniv() { return nitUniv; }
	public String getPlacaInventario() { return placaInventario; }
	public TipoMovimiento getTipoMovimiento() { return tipoMovimiento; }
	public LocalDateTime getFechaMovimiento() { return fechaMovimiento; }
	public String getNumDocumento() { return numDocumento; }
	public String getIdempotencyKey() { return idempotencyKey; }
}
