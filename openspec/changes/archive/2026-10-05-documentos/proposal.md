# Proposal: Documentos Service (Task 9)

## Intent

`foliox-documentos` is a stub (Application + application.yml, port 8084). Deliver PDF management per `spects.txt`: upload to MinIO, tenant-scoped CRUD, expediente association, gated deletion (ADMIN always; others only if expediente CERRADO). Repo's first inter-service HTTP call. Unblocks plantillas (task 10).

## Scope

### In Scope
- Table `documentos` in DB `foliox_documentos` via `schema.sql` (`sql.init.mode: always`).
- `/api/documentos`: POST multipart upload (201), GET list/metadata, GET content streaming (`application/pdf`), PATCH rename/re-associate, DELETE (rule below).
- PDF only: content-type + `%PDF-` magic bytes; cap via `spring.codec.max-in-memory-size` + 413 handler.
- MinIO: `io.minio:minio` 8.6.x `MinioAsyncClient` behind `DocumentoStorage`; key `{estudioId}/{uuid}.pdf`; explicit pom version (not BOM-managed).
- `ExpedienteClient` (WebClient + JWT relay): validate on create (404 propagates), read estado on non-admin delete; 5s timeout + 502 ApiError mapping.
- Deletion: ADMIN always; others only if CERRADO (else 403); hard delete row-first, then MinIO object (log cleanup failure).
- Mirrors `foliox-expedientes` wiring: SecurityConfig, handler (+413), tenancy from JWT `estudio_id` only, springdoc multipart operable.

### Out of Scope
- Plantillas (task 10); tests (task 23); service docs (task 24); virus scanning; gateway routing.

## Capabilities

### New Capabilities
- `documentos-crud`: PDF document lifecycle (upload to MinIO, metadata, content streaming, rename/re-associate, role+estado-gated delete), JWT tenant scoping.

### Modified Capabilities
- None — `expedientes-crud` unchanged (its expediente GET reused read-only).

## Approach

Exploration recommendation **A1+B1+C1+D**:
- R2DBC: `id UUID PK, estudio_id UUID NOT NULL + index, expediente_id UUID, nombre, ubicacion, creado_en` — plain UUIDs, no cross-DB FK (task 8 precedent).
- `DocumentoStorage` interface + MinIO impl; `boundedElastic` only around storage ops.
- `ExpedienteClient.get(id)` relays caller `Authorization` to expedientes; failure → 502, never fail open.
- Delete: ADMIN bypasses check; others require live `estado == CERRADO` else 403.

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `foliox-documentos/pom.xml` | Modified | r2dbc, security, oauth2-resource-server, validation, common, minio |
| `foliox-documentos/src/main/resources/{application.yml,schema.sql}` | Modified/New | R2DBC URL, JWK, MinIO props, codec limit, DDL |
| `foliox-documentos/src/main/java/com/foliox/documentos/**` | New | config/, web/, documento/, storage/, client/ |
| `foliox-expedientes` | None | read-only reuse of expediente GET |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| First inter-service HTTP call | Med | 5s timeout, isolated client, 502 ApiError |
| MinIO SDK blocking WebFlux thread | Med | `boundedElastic` scoped to storage ops |
| Oversized upload → `DataBufferLimitException` | Med | handler → 413 |
| Orphans / MinIO cleanup fails | Med | no-FK precedent; row-first delete + cleanup log |
| springdoc multipart not operable | Med | verify `FilePart` in Swagger (solved once for `/oauth2/token`) |

## Rollback Plan

Merge only after verify; revert PR and drop `documentos` table + MinIO objects (disposable pre-launch). No other service consumes foliox-documentos.

## Dependencies

Tasks 7 & 8 merged (JWT claims, expediente GET with estado + 404 cross-estudio); dep 2 MinIO bucket `documentos`.

## Verification Strategy

sdd-verify: curl flow on running stack — upload/get/patch/delete across role×estado matrix; assert 400/404/403/413/502; Swagger multipart check. Tests deferred to task 23.

## Success Criteria

- [ ] Non-PDF → 400; oversized → 413; object at `{estudioId}/{uuid}.pdf`.
- [ ] Cross-estudio → 404; `estudio_id` never from body.
- [ ] DELETE: ADMIN → 204; others → 204 iff CERRADO else 403; expedientes down → 502.
