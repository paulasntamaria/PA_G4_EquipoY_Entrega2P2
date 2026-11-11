-- Idempotencia del POST de movimientos: el cliente puede mandar el header
-- Idempotency-Key. Si reintenta con la misma clave, se devuelve el movimiento
-- ya registrado en vez de crear otro. La clave es unica por universidad y es
-- opcional (los movimientos sin clave siguen funcionando como antes).
ALTER TABLE movimiento_inventario ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(100);
CREATE UNIQUE INDEX IF NOT EXISTS uq_movimiento_idempotency_key
 ON movimiento_inventario(nit_univ,idempotency_key) WHERE idempotency_key IS NOT NULL;
