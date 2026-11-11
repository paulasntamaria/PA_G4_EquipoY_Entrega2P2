CREATE TABLE IF NOT EXISTS activo_fijo (
 nit_univ VARCHAR(20) NOT NULL, placa_inventario VARCHAR(50) NOT NULL, descripcion VARCHAR(255) NOT NULL,
 cod_unspsc VARCHAR(50) NOT NULL, cod_sede VARCHAR(20) NOT NULL, valor_contable NUMERIC(19,4) NOT NULL,
 estado VARCHAR(50) NOT NULL, CONSTRAINT pk_activo_fijo PRIMARY KEY(nit_univ,placa_inventario),
 CONSTRAINT chk_activo_estado CHECK(estado IN ('ACTIVO','MANTENIMIENTO','BAJA')), CONSTRAINT chk_activo_valor CHECK(valor_contable>=0));
CREATE INDEX IF NOT EXISTS idx_activo_nit ON activo_fijo(nit_univ);
INSERT INTO activo_fijo VALUES
 ('900123456-1','AF-2026-0001','Computador portatil HP EliteBook 840 G9','43211503','SED-01',3500000,'ACTIVO'),
 ('900123456-1','AF-2026-0002','Videobeam Epson PowerLite X49','45111609','SED-01',2100000,'ACTIVO')
ON CONFLICT (nit_univ,placa_inventario) DO NOTHING;
