package com.eter.eterbackend.activos.infrastructure.adapter.out.persistence;

import java.time.LocalDateTime;
import java.util.UUID;

import com.eter.eterbackend.activos.domain.model.EstadoPrestamo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "prestamo_temporal")
public class PrestamoTemporalJpaEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(name = "id_prestamo", nullable = false, columnDefinition = "uuid", updatable = false)
	private UUID idPrestamo;

	@Column(name = "nit_univ", length = 20, nullable = false)
	private String nitUniv;

	@Column(name = "placa_inventario", length = 50, nullable = false)
	private String placaInventario;

	@Column(name = "num_documento", length = 20, nullable = false)
	private String numDocumento;

	@Column(name = "fecha_salida", nullable = false)
	private LocalDateTime fechaSalida;

	@Column(name = "fecha_devolucion_pactada", nullable = false)
	private LocalDateTime fechaDevolucionPactada;

	@Column(name = "fecha_devolucion_real")
	private LocalDateTime fechaDevolucionReal;

	@Enumerated(EnumType.STRING)
	@Column(name = "estado", length = 20, nullable = false)
	private EstadoPrestamo estado;

	protected PrestamoTemporalJpaEntity() {
	}

	public PrestamoTemporalJpaEntity(UUID idPrestamo, String nitUniv, String placaInventario, String numDocumento,
			LocalDateTime fechaSalida, LocalDateTime fechaDevolucionPactada,
			LocalDateTime fechaDevolucionReal, EstadoPrestamo estado) {
		this.idPrestamo = idPrestamo;
		this.nitUniv = nitUniv;
		this.placaInventario = placaInventario;
		this.numDocumento = numDocumento;
		this.fechaSalida = fechaSalida;
		this.fechaDevolucionPactada = fechaDevolucionPactada;
		this.fechaDevolucionReal = fechaDevolucionReal;
		this.estado = estado;
	}

	public UUID getIdPrestamo() { return idPrestamo; }
	public String getNitUniv() { return nitUniv; }
	public String getPlacaInventario() { return placaInventario; }
	public String getNumDocumento() { return numDocumento; }
	public LocalDateTime getFechaSalida() { return fechaSalida; }
	public LocalDateTime getFechaDevolucionPactada() { return fechaDevolucionPactada; }
	public LocalDateTime getFechaDevolucionReal() { return fechaDevolucionReal; }
	public EstadoPrestamo getEstado() { return estado; }
}
