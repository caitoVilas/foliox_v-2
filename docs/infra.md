# Infraestructura local (Docker Compose)

Stack de desarrollo: PostgreSQL 16, Kafka 4.3.1 (KRaft, sin ZooKeeper), MinIO.

## Arrancar / parar / reiniciar

```bash
Copy-Item .env.example .env   # UNA sola vez: completar credenciales reales
docker compose up -d      # arranca todo (no espera: para esperar healthy usa `up -d --wait`)
docker compose ps         # estado: todas las services en "healthy"
docker compose down       # para sin borrar datos
docker compose down -v    # PARA Y BORRA los datos (reset total)
```

Sin `.env` el arranque **falla rápido a propósito**: `POSTGRES_PASSWORD` y
`MINIO_ROOT_PASSWORD` no tienen default en `docker-compose.yml` (`${VAR:?}`).
Lo mismo aplica a los servicios Spring: sus `application.yml` no tienen default
y abortan el boot si falta un secreto (ver `.env.example` para la lista completa).

La primera ejecución tarda más (pull de imágenes + creación de BDs y bucket).

## Endpoints

| Servicio   | Desde el host            | Credenciales                |
|------------|--------------------------|-----------------------------|
| PostgreSQL | `localhost:5432`         | ver `.env` (`POSTGRES_*`)   |
| Kafka      | `localhost:9092`         | sin auth                    |
| MinIO API  | `localhost:9000`         | ver `.env` (`MINIO_*`)      |
| MinIO Consola | `localhost:9001`      | idem                        |

- PostgreSQL: 6 BDs (`foliox_auth`, `foliox_usuarios`, `foliox_expedientes`,
  `foliox_documentos`, `foliox_agenda`, `foliox_notificaciones`), creadas por
  `infra/postgres/init/01-create-databases.sql` solo en el primer arranque.
- MinIO: bucket `documentos` creado automáticamente en el primer arranque
  (soporte nativo `MINIO_DEFAULT_BUCKETS`, idempotente).
- Variables (credenciales, bucket): ver `.env.example`.
- Imagen MinIO: upstream eliminó `minio/minio` de Docker Hub (sep 2026) y puso
  quay.io tras login; se usa el espejo público `bitnamilegacy/minio` (pinned).

## Kafka: dos listeners (el gotcha de siempre)

| Cliente                     | Bootstrap server      |
|-----------------------------|-----------------------|
| Servicios Spring en el host | `localhost:9092`      |
| Contenedores en esta red    | `kafka:19092`         |

El broker **anuncia** (`advertised.listeners`) `localhost:9092` para el host y
`kafka:19092` para la red interna: el cliente recibe esa dirección del metadata
y reconecta contra ella. Si un cliente anuncia mal (p. ej. `kafka:9092`), el
host falla con timeout tras el handshake. Regla: **la dirección anunciada debe
ser alcanzable desde donde corre el cliente**.

## Logs

```bash
docker compose logs -f kafka     # -f para seguir; o sin -f para el histórico
docker compose logs minio        # debe decir "Creating default buckets..." / "ready"
```

## Problemas típicos

- **Service "unhealthy"**: `docker compose logs <servicio>` → casi siempre es
  arranque lento (el healthcheck tiene `start_period`) o puerto ocupado en el host.
- **BDs o bucket ausentes**: existen solo con volumen nuevo → `down -v` y `up -d`.
