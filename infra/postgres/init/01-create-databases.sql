-- Foliox: one database per service.
-- Executed by the official postgres image ONLY on first boot (empty data volume),
-- as /docker-entrypoint-initdb.d script. Re-running requires: docker compose down -v.
-- Owner defaults to POSTGRES_USER, which is what each service uses to connect.

CREATE DATABASE foliox_auth;
CREATE DATABASE foliox_usuarios;
CREATE DATABASE foliox_expedientes;
CREATE DATABASE foliox_documentos;
CREATE DATABASE foliox_agenda;
CREATE DATABASE foliox_notificaciones;
