CREATE TABLE IF NOT EXISTS notificacion (
 id_notificacion UUID NOT NULL DEFAULT gen_random_uuid(),event_id UUID NOT NULL,nit_univ VARCHAR(20) NOT NULL,destinatario_email VARCHAR(100) NOT NULL,asset_key VARCHAR(100) NOT NULL,
 placa_inventario VARCHAR(50) NOT NULL,tipo VARCHAR(80) NOT NULL,titulo VARCHAR(160) NOT NULL,mensaje VARCHAR(1000) NOT NULL,leida BOOLEAN NOT NULL DEFAULT FALSE,creada_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,leida_en TIMESTAMPTZ,
 CONSTRAINT pk_notificacion PRIMARY KEY(id_notificacion),CONSTRAINT uq_notificacion_evento_dest UNIQUE(event_id,destinatario_email),CONSTRAINT chk_notificacion_lectura CHECK((leida=FALSE AND leida_en IS NULL) OR leida=TRUE));
CREATE INDEX IF NOT EXISTS idx_notificacion_bandeja ON notificacion(nit_univ,destinatario_email,leida,creada_en DESC);
