package com.eter.eterbackend.shared.events.infrastructure;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "outbox_event")
public class OutboxEventJpaEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(name = "event_id", nullable = false, columnDefinition = "uuid", updatable = false)
	private UUID eventId;

	@Column(name = "dedup_key", length = 180, nullable = false, unique = true)
	private String dedupKey;

	@Column(name = "aggregate_type", length = 50, nullable = false)
	private String aggregateType;

	@Column(name = "aggregate_key", length = 100, nullable = false)
	private String aggregateKey;

	@Column(name = "event_type", length = 80, nullable = false)
	private String eventType;

	@Column(name = "event_version", nullable = false)
	private Short eventVersion;

	@Column(name = "nit_univ", length = 20, nullable = false)
	private String nitUniv;

	@Column(name = "placa_inventario", length = 50, nullable = false)
	private String placaInventario;

	@Column(name = "actor", length = 100, nullable = false)
	private String actor;

	@Column(name = "payload", nullable = false, columnDefinition = "text")
	private String payload;

	@Column(name = "occurred_at", nullable = false)
	private OffsetDateTime occurredAt;

	@Column(name = "published_at")
	private OffsetDateTime publishedAt;

	@Column(name = "publish_attempts", nullable = false)
	private Integer publishAttempts;

	@Column(name = "last_error", columnDefinition = "text")
	private String lastError;

	protected OutboxEventJpaEntity() {
	}

	public OutboxEventJpaEntity(String dedupKey, String aggregateKey, String eventType, String nitUniv,
			String placaInventario, String actor, String payload, OffsetDateTime occurredAt) {
		this.dedupKey = dedupKey;
		this.aggregateType = "ACTIVO";
		this.aggregateKey = aggregateKey;
		this.eventType = eventType;
		this.eventVersion = 1;
		this.nitUniv = nitUniv;
		this.placaInventario = placaInventario;
		this.actor = actor;
		this.payload = payload;
		this.occurredAt = occurredAt;
		this.publishAttempts = 0;
	}

	public UUID getEventId() { return eventId; }
	public String getDedupKey() { return dedupKey; }
	public String getAggregateKey() { return aggregateKey; }
	public String getEventType() { return eventType; }
	public String getNitUniv() { return nitUniv; }
	public String getPlacaInventario() { return placaInventario; }
	public String getActor() { return actor; }
	public String getPayload() { return payload; }
	public OffsetDateTime getOccurredAt() { return occurredAt; }
	public OffsetDateTime getPublishedAt() { return publishedAt; }

	public void marcarPublicado() {
		this.publishedAt = OffsetDateTime.now();
		this.lastError = null;
	}

	public void registrarError(String error) {
		this.publishAttempts = this.publishAttempts + 1;
		this.lastError = error;
	}
}
