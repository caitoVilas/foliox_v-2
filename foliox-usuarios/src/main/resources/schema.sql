-- schema.sql (foliox-usuarios es dueño de estas tablas)
CREATE TABLE IF NOT EXISTS estudios (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nombre VARCHAR(255) NOT NULL,
    creado_en TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS usuarios (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(100) NOT NULL,
    nombre VARCHAR(255) NOT NULL,
    rol VARCHAR(20) NOT NULL,
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    estudio_id UUID NOT NULL REFERENCES estudios (id)
);

CREATE INDEX IF NOT EXISTS ix_usuarios_estudio_id ON usuarios (estudio_id);
