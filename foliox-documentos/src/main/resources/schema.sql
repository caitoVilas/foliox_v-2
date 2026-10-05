-- schema.sql (foliox-documentos es dueño de estas tablas)
-- DDL sin FK cross-DB (precedente task 8): expedientes vive en otra base de datos.
CREATE TABLE IF NOT EXISTS documentos (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    estudio_id UUID NOT NULL,
    expediente_id UUID NOT NULL,
    nombre VARCHAR(255) NOT NULL,
    ubicacion VARCHAR(255) NOT NULL,
    creado_en TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS ix_documentos_estudio_id ON documentos (estudio_id);
