CREATE TABLE IF NOT EXISTS prestamo_temporal (
 id_prestamo UUID NOT NULL DEFAULT gen_random_uuid(), nit_univ VARCHAR(20) NOT NULL, placa_inventario VARCHAR(50) NOT NULL,
 num_documento VARCHAR(20) NOT NULL, fecha_salida TIMESTAMP NOT NULL, fecha_devolucion_pactada TIMESTAMP NOT NULL,
 fecha_devolucion_real TIMESTAMP, estado VARCHAR(20) NOT NULL DEFAULT 'VIGENTE', CONSTRAINT pk_prestamo PRIMARY KEY(id_prestamo),
 CONSTRAINT chk_prestamo_fechas CHECK(fecha_devolucion_pactada>fecha_salida), CONSTRAINT chk_prestamo_estado CHECK(estado IN ('VIGENTE','DEVUELTO','CANCELADO')),
 CONSTRAINT chk_prestamo_devolucion CHECK((estado='DEVUELTO' AND fecha_devolucion_real IS NOT NULL) OR estado IN ('VIGENTE','CANCELADO')));
CREATE INDEX IF NOT EXISTS idx_prestamo_nit_placa ON prestamo_temporal(nit_univ,placa_inventario);
CREATE INDEX IF NOT EXISTS idx_prestamo_vigia ON prestamo_temporal(fecha_devolucion_pactada) WHERE estado='VIGENTE' AND fecha_devolucion_real IS NULL;
CREATE TABLE IF NOT EXISTS outbox_event (
 event_id UUID NOT NULL DEFAULT gen_random_uuid(), dedup_key VARCHAR(180) NOT NULL UNIQUE, aggregate_type VARCHAR(50) NOT NULL DEFAULT 'PRESTAMO', aggregate_key VARCHAR(100) NOT NULL,
 event_type VARCHAR(80) NOT NULL,event_version SMALLINT NOT NULL DEFAULT 1,nit_univ VARCHAR(20) NOT NULL,placa_inventario VARCHAR(50) NOT NULL,actor VARCHAR(100) NOT NULL,
 payload TEXT NOT NULL,occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,published_at TIMESTAMPTZ,publish_attempts INTEGER NOT NULL DEFAULT 0,last_error TEXT,
 CONSTRAINT pk_outbox_event PRIMARY KEY(event_id), CONSTRAINT chk_outbox_version CHECK(event_version>=1));
CREATE INDEX IF NOT EXISTS idx_outbox_pendientes ON outbox_event(occurred_at) WHERE published_at IS NULL;
