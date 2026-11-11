-- Transactional Outbox de activos: el alta o la baja del activo y su evento
-- se guardan en la misma transaccion. OutboxPublisher los publica despues en
-- eter.activos.eventos.v1 (lo consume inventario para el kardex ALTA/BAJA).
CREATE TABLE IF NOT EXISTS outbox_event (
 event_id UUID NOT NULL DEFAULT gen_random_uuid(), dedup_key VARCHAR(180) NOT NULL UNIQUE, aggregate_type VARCHAR(50) NOT NULL DEFAULT 'ACTIVO', aggregate_key VARCHAR(100) NOT NULL,
 event_type VARCHAR(80) NOT NULL,event_version SMALLINT NOT NULL DEFAULT 1,nit_univ VARCHAR(20) NOT NULL,placa_inventario VARCHAR(50) NOT NULL,actor VARCHAR(100) NOT NULL,
 payload TEXT NOT NULL,occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,published_at TIMESTAMPTZ,publish_attempts INTEGER NOT NULL DEFAULT 0,last_error TEXT,
 CONSTRAINT pk_outbox_event PRIMARY KEY(event_id), CONSTRAINT chk_outbox_version CHECK(event_version>=1));
CREATE INDEX IF NOT EXISTS idx_outbox_pendientes ON outbox_event(occurred_at) WHERE published_at IS NULL;
