# Tasks: Documentos Service (Task 9)

Tests deferred (23); paths `foliox-documentos/`.

## Phase 1: Foundation

- [x] 1.1 **Deps + runtime config** — Files: `pom.xml` (r2dbc, security, oauth2, validation, common, `io.minio:minio:8.6.0`), `application.yml` (8084, `foliox_documentos`, JWK, `sql.init`, codec+multipart `25MB`, `app.minio.*`, `app.expedientes.base-url`) — Verify: compile — ~55 lines
- [x] 1.2 **Schema + MinIO/OpenAPI** — Files: `schema.sql` (design DDL + `ix_documentos_estudio_id`), `config/MinioConfig.java` (`MinioAsyncClient`), `DocumentosApplication.java` (`@OpenAPIDefinition` + `@SecurityScheme`) — Verify: compile; boot 4.1 — ~57 lines

## Phase 2: Domain core

- [x] 2.1 **Domain types** — Files: `ActualizarDocumentoCommand` (null=absent, no estudio_id), `DocumentoResponse`, `ExpedienteResumen`, `Documento`, `DocumentoRepository` (2 queries), `ArchivoNoValidoException`, `ServicioNoDisponibleException` — Verify: compile — ~100 lines
- [x] 2.2 **Storage seam + MinIO impl** — Files: `storage/DocumentoStorage.java`, `storage/MinioDocumentoStorage.java`: `subscribeOn(boundedElastic)` here only (D2), `NoSuchKey` → empty — Verify: compile; thread check 4.2 — ~90 lines
- [x] 2.3 **ExpedienteClient, JWT relay** — File: `client/ExpedienteClient.java` (D3): relay `Authorization`, `.timeout(5s)` on Mono, failure → `ServicioNoDisponibleException` 502, 404 propagates — Verify: compile — ~70 lines
- [x] 2.4 **Service upload, app-level size counting** — File: `DocumentoService.crear` (D4): content-type 400 → CL precheck → count bytes → cap → `DataBufferLimitException` 413 → `%PDF-` 400 → expediente 404/502 → MinIO → insert; fail → `eliminar`+log — Verify: compile — ~110 lines
- [x] 2.5 **Service read + PATCH** — Same file: scoped `listar`/`obtener`, `contenido` via `DataBufferUtils.readInputStream` (missing → 502), `actualizar` 404-first → re-validate expediente → apply non-null — Verify: compile — ~85 lines
- [x] 2.6 **Service delete, row-first** — Same file: 404 before role check; ADMIN bypass; estado ≠ CERRADO → 403; row delete → MinIO `eliminar` `onErrorResume` → log, 204 — Verify: compile — ~70 lines

## Phase 3: Web/security wiring

- [x] 3.1 **SecurityConfig + 401 ApiError** — File: `config/SecurityConfig.java`: expedientes copy (public swagger, ApiError 401 español, rol→ROLE, CSRF off) — Verify: compile — ~65 lines
- [x] 3.2 **GlobalExceptionHandler** — File: `web/GlobalExceptionHandler.java`: expedientes copy + `ArchivoNoValidoException`→400, `DataBufferLimitException`→413, `ServicioNoDisponibleException`→502, Spanish — Verify: compile — ~135 lines
- [x] 3.3 **Controller, six endpoints** — File: `web/DocumentoController.java`: POST multipart `consumes` + `@RequestHeader(Authorization)`, GET list/`{id}`/contenido, PATCH/DELETE relay, JWT helpers — Verify: compile — ~110 lines

## Phase 4: Boot + smoke

- [x] 4.1 **Boot + Swagger + upload curl** — Verify: schema applied; six endpoints, bearerAuth, multipart `file`+`expedienteId` executable; no token 401; ASISTENTE 201, spoofed estudio ignored, `{A}/{uuid}.pdf`; non-PDF/missing file 400; >cap 413; unknown/foreign expediente 404, no row/object; expedientes down 502
- [x] 4.2 **Read curl** — Verify: list only caller estudio; own 200; cross-tenant metadata/content 404; download 200 `application/pdf` + attachment; object removed → 502
- [x] 4.3 **PATCH/delete curl** — Verify: rename+re-assoc 200 estudioId unchanged; foreign PATCH 404 unchanged; ADMIN delete MinIO stopped → 204, GET 404; ABOGADO CERRADO 204 / EN_TRAMITE 403; cross-tenant delete 404 not 403; expedientes down → 502, GET 200

## Review Workload Forecast

Estimated changed lines: 950–1100
Decision needed before apply: Yes
Chained PRs recommended: Yes
Chain strategy: pending
400-line budget risk: High

- **Work units (plan only):** PR 1 ← 1.1–1.2, 2.1–2.2 (~310); PR 2 ← 2.3–2.6 (~340); PR 3 ← 3.1–3.3, 4.1–4.3 (~310)
- One commit per unit; bases: 1→main, 2→PR 1, 3→PR 2.

## Delivery

- Decision: single PR with maintainer-approved `size:exception` (forecast 950-1100 vs 400 budget, approved 2026-10-01).
- Branch: `feature/0009-documentos` from `develop`.

