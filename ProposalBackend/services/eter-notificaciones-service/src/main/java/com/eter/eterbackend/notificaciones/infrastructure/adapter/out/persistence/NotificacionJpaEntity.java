package com.eter.eterbackend.notificaciones.infrastructure.adapter.out.persistence;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "notificacion")
public class NotificacionJpaEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(name = "id_notificacion", nullable = false, columnDefinition = "uuid", updatable = false)
	private UUID idNotificacion;

	@Column(name = "event_id", nullable = false, columnDefinition = "uuid")
	private UUID eventId;

	@Column(name = "nit_univ", length = 20, nullable = false)
	private String nitUniv;

	@Column(name = "destinatario_email", length = 100, nullable = false)
	private String destinatarioEmail;

	@Column(name = "asset_key", length = 100, nullable = false)
	private String assetKey;

	@Column(name = "placa_inventario", length = 50, nullable = false)
	private String placaInventario;

	@Column(name = "tipo", length = 80, nullable = false)
	private String tipo;

	@Column(name = "titulo", length = 160, nullable = false)
	private String titulo;

	@Column(name = "mensaje", length = 1000, nullable = false)
	private String mensaje;

	@Column(name = "leida", nullable = false)
	private boolean leida;

	@Column(name = "creada_en", nullable = false)
	private OffsetDateTime creadaEn;

	@Column(name = "leida_en")
	private OffsetDateTime leidaEn;

	protected NotificacionJpaEntity() {
	}

	public NotificacionJpaEntity(UUID eventId, String nitUniv, String destinatarioEmail, String assetKey,
			String placaInventario, String tipo, String titulo, String mensaje) {
		this.eventId = eventId;
		this.nitUniv = nitUniv;
		this.destinatarioEmail = destinatarioEmail;
		this.assetKey = assetKey;
		this.placaInventario = placaInventario;
		this.tipo = tipo;
		this.titulo = titulo;
		this.mensaje = mensaje;
		this.leida = false;
		this.creadaEn = OffsetDateTime.now();
	}

	public UUID getIdNotificacion() { return idNotificacion; }
	public UUID getEventId() { return eventId; }
	public String getNitUniv() { return nitUniv; }
	public String getDestinatarioEmail() { return destinatarioEmail; }
	public String getAssetKey() { return assetKey; }
	public String getPlacaInventario() { return placaInventario; }
	public String getTipo() { return tipo; }
	public String getTitulo() { return titulo; }
	public String getMensaje() { return mensaje; }
	public boolean isLeida() { return leida; }
	public OffsetDateTime getCreadaEn() { return creadaEn; }
	public OffsetDateTime getLeidaEn() { return leidaEn; }

	public void marcarLeida(OffsetDateTime cuando) {
		this.leida = true;
		this.leidaEn = cuando;
	}
}
