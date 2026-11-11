package com.eter.eterbackend.activos.infrastructure.adapter.out.persistence;

import java.math.BigDecimal;

import com.eter.eterbackend.activos.domain.model.EstadoActivo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

@Entity
@Table(name = "activo_fijo")
@IdClass(ActivoFijoId.class)
public class ActivoFijoJpaEntity {

	@Id
	@Column(name = "nit_univ", length = 20, nullable = false)
	private String nitUniv;

	@Id
	@Column(name = "placa_inventario", length = 50, nullable = false)
	private String placaInventario;

	@Column(name = "descripcion", length = 255, nullable = false)
	private String descripcion;

	@Column(name = "cod_unspsc", length = 50, nullable = false)
	private String codUnspsc;

	@Column(name = "cod_sede", length = 20, nullable = false)
	private String codSede;

	@Column(name = "valor_contable", nullable = false, precision = 19, scale = 4)
	private BigDecimal valorContable;

	@Enumerated(EnumType.STRING)
	@Column(name = "estado", length = 50, nullable = false)
	private EstadoActivo estado;

	protected ActivoFijoJpaEntity() {
	}

	public ActivoFijoJpaEntity(String nitUniv, String placaInventario, String descripcion, String codUnspsc,
			String codSede, BigDecimal valorContable, EstadoActivo estado) {
		this.nitUniv = nitUniv;
		this.placaInventario = placaInventario;
		this.descripcion = descripcion;
		this.codUnspsc = codUnspsc;
		this.codSede = codSede;
		this.valorContable = valorContable;
		this.estado = estado;
	}

	public String getNitUniv() { return nitUniv; }
	public String getPlacaInventario() { return placaInventario; }
	public String getDescripcion() { return descripcion; }
	public String getCodUnspsc() { return codUnspsc; }
	public String getCodSede() { return codSede; }
	public BigDecimal getValorContable() { return valorContable; }
	public EstadoActivo getEstado() { return estado; }
}
