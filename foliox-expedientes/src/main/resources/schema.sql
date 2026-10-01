-- schema.sql (foliox-expedientes es dueño de estas tablas)
CREATE TABLE IF NOT EXISTS expedientes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    estudio_id UUID NOT NULL,
    nombre VARCHAR(255),
    fuero VARCHAR(20) NOT NULL,
    estado VARCHAR(20) NOT NULL DEFAULT 'INGRESADO',
    creado_en TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Migración idempotente para tablas ya existentes (sin pérdida de datos)
ALTER TABLE expedientes ADD COLUMN IF NOT EXISTS nombre VARCHAR(255);
UPDATE expedientes SET nombre = 'Expediente' WHERE nombre IS NULL;

CREATE INDEX IF NOT EXISTS ix_expedientes_estudio_id ON expedientes (estudio_id);
