# Proposal: Expedientes Service (Task 8)

## Intent

The `foliox-expedientes` module is a stub (Application + application.yml only). Deliver the core domain entity: expedientes with `fuero`/`estado`, queryable by all roles, tenant-scoped to the caller's estudio, deletable only by ADMIN. Unblocks documentos (task 9) and agenda (task 11).

## Scope

### In Scope
- Table `expedientes` in DB `foliox_expedientes` via `schema.sql` (`spring.sql.init.mode: always`).
- CRUD endpoints under `/api/expedientes`; list accepts optional `fuero`/`estado` query filters (AND-combined).
- Multi-tenancy: `estudio_id` always from JWT claim, never from request body; every query scoped by it.
- Security: delete = `ROLE_ADMIN` only; create/update/get/list = any authenticated role of the estudio.
- WebFlux + R2DBC wiring mirroring `foliox-usuarios`: SecurityConfig (JWT → `ROLE_<rol>`), GlobalExceptionHandler, DTOs, springdoc, pom deps (r2dbc, security, common).

### Out of Scope
- Tests (task 23) and service docs `.md` (task 24).
- Documentos↔expediente association (task 9) — model must not block adding `expediente_id` later.
- Estado transition rules, invitations (task 6), gateway routing.

## Capabilities

### New Capabilities
- `expedientes-crud`: expediente lifecycle (create, list with fuero/estado filters, get, update, delete), JWT tenant scoping, and role-based deletion.

### Modified Capabilities
- None — no `openspec/specs/` exists yet.

## Approach

- Reactive repository (R2DBC) over table: `id UUID PK, estudio_id UUID NOT NULL + index, fuero VARCHAR, estado VARCHAR (default INGRESADO), creado_en TIMESTAMPTZ`. Plain `UUID estudio_id` — no cross-DB FK (estudios live in `foliox_usuarios`).
- Enums from `foliox-common` (`Fuero`, `EstadoExpediente`) for validation.
- Endpoint sketch: `POST /api/expedientes` · `GET /api/expedientes?fuero=&estado=` · `GET/PUT /api/expedientes/{id}` · `DELETE /api/expedientes/{id}` (ADMIN).

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `foliox-expedientes/pom.xml` | Modified | Add r2dbc, security, common deps |
| `foliox-expedientes/src/main/resources/{application.yml,schema.sql}` | Modified/New | R2DBC URL `foliox_expedientes`, JWK, table DDL |
| `foliox-expedientes/src/main/java/com/foliox/expedientes/**` | New | web/, config/, expediente/ (entity, repo, service, DTOs) |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| No FK across DBs → orphan `estudio_id` possible | Med | Index only; service never trusts body-sent `estudio_id` |
| Estado workflow undefined (who/when transitions) | Med | Spec: update endpoint may change estado; no transition validation this task |
| Delete semantics ambiguous (hard vs. soft) | Low | Propose hard delete; documentos dependents handled in task 9 |

## Rollback Plan

Additive change: revert the PR and drop `expedientes` table. No other shipped service reads it yet (task 9 not started).

## Dependencies

- Task 7 complete (usuarios Resource Server, JWT claims `rol`/`estudio_id`).
- `foliox-common` enums; DB `foliox_expedientes` exists.

## Success Criteria

- [ ] List filtered by fuero and/or estado returns only caller's estudio rows.
- [ ] DELETE returns 403 for ABOGADO/ASISTENTE, 204 for ADMIN.
- [ ] Cross-estudio access by id returns 404.
