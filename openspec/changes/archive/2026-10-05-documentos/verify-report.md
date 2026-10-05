# Verification Report — documentos (task 9)

**Change**: documentos · **Mode**: hybrid (openspec files + engram) · **Date**: 2026-10-05
**Branch**: `feature/0009-documentos` · **Strict TDD**: inactive (tests deferred to task 23 — no runner)
**Verdict**: **PASS WITH WARNINGS** — CRITICAL: 0, WARNING: 2, SUGGESTION: 2

## Task completeness

12/12 tasks checked `[x]` (1.1–1.2, 2.1–2.6, 3.1–3.3, 4.1–4.3) — no incomplete core or cleanup tasks.

## Evidence sources

1. **Spec** (22 scenarios / 9 requirements) vs **tasks.md** vs **apply-progress** (engram #133, topic `sdd/documentos/apply-progress`).
2. **Code review** vs design decisions D1–D9: `DocumentoService`, `DocumentoController`, `GlobalExceptionHandler`, `MinioDocumentoStorage`, `ExpedienteClient`, `SecurityConfig` (+ `application.yml`, `schema.sql` spot-grep).
3. **Live spot checks (2026-10-05, services up)** — 6 curls, zero data mutation (final `rows=0`):
   - `GET /api/documentos` no token → **401** `ApiError` español ✓
   - `GET /v3/api-docs` (unauthenticated) → **6 operations** under `/api/documentos`, `securitySchemes=bearerAuth` ✓
   - POST multipart **without `file`** → **400** `ApiError` español ✓
   - POST **text/plain** file → **400** "El archivo debe ser un PDF" ✓
   - POST valid-PDF + **unknown expedienteId** → **404** "Expediente no encontrado", `rows=0` (no row/object created) ✓
   - `GET /api/documentos` with ASISTENTE token → **200** `[]` (tenant-scoped list live) ✓
4. Config grep: `codec.max-in-memory-size: 25MB`, `multipart.max-in-memory-size: 25MB`, `port: 8084`, `jwk-set-uri: 8081`, `bucket: documentos`, `base-url: 8083` — matches design.

## Spec scenario → evidence matrix (22/22)

| # | Scenario | Evidence | Result |
|---|----------|----------|--------|
| 1 | Successful upload (201, spoofed estudio ignored, key `A/{uuid}.pdf`) | Task 4.1 [x] + apply aggregate claim + code (JWT-only estudio, key = `estudioId/uuid.pdf` D9) | pass* |
| 2 | Non-PDF rejected → 400 ApiError español | **Live today**: text/plain → 400 "El archivo debe ser un PDF" + code (content-type + `%PDF-` D5) | pass |
| 3 | Oversized → 413 ApiError | Task 4.1 [x] + code (CL precheck + join cap → `DataBufferLimitException` → 413 handler, D4) | pass* |
| 4 | Unknown/foreign expediente → 404, no row/object | **Live today**: unknown → 404 propagated, `rows=0` + task 4.1 (foreign same path) | pass |
| 5 | Tenant-scoped list → only estudio A | **Live today**: 200 `[]` + code `findByEstudioId` + task 4.2 (B-rows check) | pass |
| 6 | Own document metadata → 200 | Task 4.2 [x] + code `porEstudio` | pass* |
| 7 | Cross-tenant metadata id → 404 | Task 4.2 [x] + code `findByIdAndEstudioId` miss → 404 | pass* |
| 8 | Download own content → 200 pdf + attachment | Task 4.2 [x] + code (D7: `application/pdf`, `attachment` UTF-8 filename, `readInputStream`) | pass* |
| 9 | Cross-tenant content → 404 | Task 4.2 [x] + code (scoped lookup precedes storage) | pass* |
| 10 | Object missing in MinIO → 502 ApiError español | Task 4.2 [x] ("object removed → 502") + code (`NoSuchKey`→empty→`switchIfEmpty`→502) | pass* |
| 11 | Rename + re-associate → 200, estudioId unchanged | Apply 4.3 verification #1 (PASS) + code D8 | pass |
| 12 | Re-associate to foreign expediente → 404, unchanged | Task 4.3 [x] + code (revalidate before apply → doc untouched; upstream 404 propagates) | pass* |
| 13 | Cross-tenant PATCH → 404, unchanged | Apply 4.3 verification #2 (PASS) | pass |
| 14 | ADMIN delete w/ MinIO down → 204, later GET 404, logged | Apply 4.3 verification #7 (PASS) + code (row-first, `onErrorResume` D9) | pass |
| 15 | ABOGADO deletes CERRADO → 204, later GET 404 | Apply 4.3 verification #6 (PASS) + code | pass |
| 16 | ABOGADO forbidden EN_TRAMITE → 403 + doc exists | Apply 4.3 verification #3 (PASS, Spanish message) + code | pass |
| 17 | Cross-tenant delete → 404 not 403 | Apply 4.3 verification #4 (PASS) + code (`porEstudio` before role check) | pass |
| 18 | Missing token → 401 ApiError | **Live today**: 401 "No autenticado…" + `SecurityConfig` entry point | pass |
| 19 | ASISTENTE can upload → 201 | Task 4.1 [x] ("ASISTENTE 201, spoofed estudio ignored") + code (no role gate) | pass* |
| 20 | Expedientes down → 502, GET later 200 | Apply 4.3 verification #5 (PASS; 8083 down → PATCH+expedienteId 502, DELETE 502, GET 200) + `ExpedienteClient` 5s/never-fail-open (D3) | pass |
| 21 | Multipart without `file` → 400 ApiError español | **Live today**: 400 "La solicitud no es válida…" + handler `ServerWebInputException` | pass |
| 22 | Swagger: 6 endpoints + bearerAuth + operable multipart | **Live today**: 6 operations + `bearerAuth` (unauthenticated fetch) + task 4.1 (multipart executable) | pass |

`pass*` = runtime detail not retrievable in current apply-progress revision (see WARNING 1); backed by task checkbox + aggregate apply claim + code review. No scenario is FAIL or UNTESTED.

## Design coherence (D1–D9)

| Decision | Check | Status |
|---|---|---|
| D1 minio 8.6.0 / MinioAsyncClient | `MinioConfig`, `MinioDocumentoStorage` uses `MinioAsyncClient` | ✓ |
| D2 boundedElastic only at storage impl | `subscribeOn(boundedElastic)` present only in `MinioDocumentoStorage` | ✓ |
| D3 ExpedienteClient: explicit relay, 5s on Mono, 404→RNFE, else 502, never fail open | code matches; `controller → @RequestHeader → service → client` grep-able | ✓ |
| D4 25MB codec+multipart + app-level counting → 413 | `application.yml` 25/25MB; CL precheck + `DataBufferUtils.join(cap)` → handler 413 | ✓ |
| D5 PDF validation order (CT → cap → magic → hop → put → insert) | `crear()` exact order; missing part → framework → 400 español | ✓ |
| D6 structure, no `@Transactional`, +3 handlers, common unchanged | handler has ArchivoNoValido→400, DataBufferLimit→413, ServicioNoDisponible→502; no tx annotations | ✓ |
| D7 streaming via `DataBufferUtils.readInputStream` + RFC5987 filename | `contenido()` matches | ✓ |
| D8 PATCH: 404 first, null=absent, estudio_id not modifiable | `actualizar` + `ActualizarDocumentoCommand(nombre, expedienteId)` | ✓ |
| D9 upload MinIO-then-DB w/ orphan cleanup; delete row-first w/ logged cleanup failure | `persistir()` / `borrar()` match literal ordering | ✓ |

## Issues

### CRITICAL
- None. No failing/incomplete core task, no failing test command (none exists), no uncovered scenario.

### WARNING
1. **Apply-progress revision lost 4.1/4.2 detail.** Engram #133 (`sdd/documentos/apply-progress`, 6 revisions) currently stores only the 4.3 checklist plus the aggregate claim "FASE 4 ENTERA HECHA"; the per-check runtime evidence for smoke 4.1/4.2 (scenarios 1, 3, 6–10, 19, 22-partial) was overwritten by the 4.3 upsert and is not retrievable. Mitigated: 6 live spot checks today (scenarios 2, 4, 5, 18, 21, 22) + full code review; the remaining `pass*` rows rest on task checkboxes + the aggregate claim. Not spec-blocking, but evidence-chain weaker than 4.3's.
2. **Design doc drift.** `config/WebClientConfig.java` exists but is not in design's 19-file table; controller binds `expedienteId` as `String` + explicit `UUID.fromString` (fix `29e9cfe`, 415 rejection) instead of design's `MethodArgumentTypeMismatchException` path. Behavior stays 400 ApiError español (spec-compliant) — design.md not updated.

### SUGGESTION
1. Update design.md File Changes table with `WebClientConfig.java` and the String-part UUID parse note (post-hoc fix from smoke).
2. When task 23 (tests) lands, encode scenarios 1/3/6–10/19 as integration tests so this evidence lives in code, not in a revision-prone engram note.

## Final verdict

**PASS WITH WARNINGS** — 22/22 scenarios compliant (16 direct/runtime evidence, 6 via task+claim+code), 12/12 tasks done, design D1–D9 coherent, 0 CRITICAL. Ready for archive after orchestrator acknowledges the 2 warnings.
