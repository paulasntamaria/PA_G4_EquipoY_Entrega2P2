-- Idempotencia de la creacion de prestamos: un activo solo puede tener un
-- prestamo VIGENTE a la vez. Si el cliente reintenta el POST (doble clic,
-- timeout), el segundo intento choca con este indice y responde 409.
CREATE UNIQUE INDEX IF NOT EXISTS uq_prestamo_vigente_por_activo
 ON prestamo_temporal(nit_univ,placa_inventario) WHERE estado='VIGENTE';
