# Design: Documentos Service (Task 9)

## Technical Approach

Build `foliox-documentos` (port **8084**) mirroring `foliox-expedientes` (entity → derived-query repository → service → controller, JWT tenancy, `schema.sql` + `sql.init.mode: always`), plus two new subsystems: **MinIO storage** (`DocumentoStorage` behind `MinioAsyncClient`) and the repo's **first inter-service HTTP call** (`ExpedienteClient`, WebClient + JWT relay → expedientes:8083). Implements all 9 requirements / 22 scenarios of `documentos-crud`.

## Architecture Decisions

| # | Decision | Rejected option (tradeoff) | Choice + rationale |
|---|----------|---------------------------|--------------------|
| 1 | MinIO SDK + version | AWS SDK v2 (heavy, S3-oriented, no BOM gain); sync `MinioClient` (blocks every call) | `io.minio:minio` **8.6.0** (verified Sep 2025 release; proposal-frozen 8.6.x line; 8.5.17 flagged 1 CVE, 9.0.3 is a fresh major — upgrade path stays one file behind the interface). `MinioAsyncClient` → `Mono.fromCompletionStage`. Explicit pom version (Boot BOM doesn't manage it). Java 8 bytecode → Java 25 safe |
| 2 | Storage seam + threading | SDK calls inside service; `Schedulers` global hooks | Interface `DocumentoStorage` (guardar/obtener/eliminar) + `MinioDocumentoStorage` impl. **`subscribeOn(boundedElastic)` applied ONLY at impl boundary** — MinIO SDK does blocking stream I/O (incl. `GetObjectResponse` reads) even behind the async facade |
| 3 | `ExpedienteClient` wiring + JWT relay | Reactor Context filter (invisible coupling); minted S2S token (touches auth) | `@Component` owning a `WebClient` (base URL `app.expedientes.base-url`, default `http://localhost:8083`). **Relay pinned: controller reads `@RequestHeader(Authorization)` → explicit `String` param → `service → client → .header(AUTHORIZATION, token)`** — grep-able, testable. Timeout pinned: `.timeout(Duration.ofSeconds(5))` **on the Mono inside the client** (bounds connect+read in one place, unlike HttpClient connectTimeout which covers connect only). Mapping inside client: `TimeoutException`/`WebClientRequestException`/unexpected status → `ServicioNoDisponibleException` (502); upstream 404 → `ResourceNotFoundException` (404 propagates). Never fail open |
| 4 | Size cap + 413 (verified Boot 4 behavior) | Rely on `spring.codec.max-in-memory-size` alone — **verified insufficient**: file parts past `spring.webflux.multipart.max-in-memory-size` (256KB default) spill to disk instead of throwing; `max-disk-usage-per-part` unenforced (spring-framework#35099, open). No `ResponseTooLargeException` exists — Framework 7 renamed `PayloadTooLargeException` → `ContentTooLargeException`; `DataBufferLimitException` remains the codec-limit exception | Set `spring.codec.max-in-memory-size: 25MB` (proposal-frozen; covers JSON + non-file parts → `DataBufferLimitException`) and `spring.webflux.multipart.max-in-memory-size: 25MB` (no disk spill under cap). **Deterministic app cap**: service injects `${spring.codec.max-in-memory-size}` as `DataSize` (single source, no drift), rejects fast on part `Content-Length`, then counts bytes while aggregating `filePart.content()` — exceed → throw `DataBufferLimitException` → handler → **413 ApiError**. Handler also catches framework-thrown `DataBufferLimitException` |
| 5 | PDF validation + where it lives | `@Valid`/interceptor (can't see bytes); storage layer (knows nothing of HTTP parts) | In `DocumentoService.crear`, cheap-first order: content-type `application/pdf` (400) → capped aggregate (413) → first-5-bytes `%PDF-` magic (400) → expediente hop (404/502) → MinIO put → insert. Magic check is trivial post-aggregation. Missing `file`/`expedienteId` parts → framework `MissingServletRequestPartException` (`ServerWebInputException` family) → existing 400 handler, Spanish. **`expedienteId` binds as `String` + explicit `UUID.fromString` (`DocumentoController.parseUuid`, fix `29e9cfe`)**: direct `UUID` part binding rejected multipart parts arriving without a part `Content-Type` (415); with String binding an invalid UUID throws `ServerWebInputException` → same 400 handler, Spanish `ApiError` (spec-compliant — post-smoke correction to this decision, verify WARNING 2) |
| 6 | Entity/service/controller structure | `@PreAuthorize` on DELETE (403 fires before lookup); adding exceptions to `foliox-common` (proposal freezes common) | Mirror expedientes: Lombok entity `@Table("documentos")`, `ReactiveCrudRepository` + 2 derived queries, records, JWT helpers `estudioId`/`isAdmin`, `SecurityConfig` copy (entry point 401 ApiError), `GlobalExceptionHandler` copy **+ 3 handlers**: `ArchivoNoValidoException`→400, `DataBufferLimitException`→413, `ServicioNoDisponibleException`→502. New exceptions in `com.foliox.documentos.exception` (common unchanged). **No `@Transactional`** (deviation): every write is a single statement (auto-commit = atomic) and blanket tx would hold the R2DBC connection across MinIO/WebClient I/O |
| 7 | Download streaming + filename | `byte[]` body (≤25MB heap × concurrent downloads); `InputStreamResource` return (WebFlux resource-writer path less predictable for blocking streams) | `storage.obtener → Mono<InputStream>` (boundedElastic), bridged with **`DataBufferUtils.readInputStream(supplier, factory, 8192)`** — lazy `Flux<DataBuffer>`, auto-closes on termination, chunked response. Headers: `Content-Type: application/pdf`, `Content-Disposition: attachment` built with `.filename(nombre, UTF_8)` → RFC 5987 `filename*` decodes ñ/á correctly. Thread placement of reads → verify in tasks (see Open Questions) |
| 8 | PATCH semantics | Requiring all fields; body `estudio_id` support | `ActualizarDocumentoCommand(String nombre, UUID expedienteId)` — **only these two mutable; null = absent** (so disassociation is impossible — spec never offers it). `estudio_id` not in record → unknown JSON property ignored (Boot `FAIL_ON_UNKNOWN_PROPERTIES=false`, expedientes precedent) → not modifiable. Order: scoped lookup **404 first** → if `expedienteId` present re-run validation (404/502, doc unchanged on failure) → apply non-null → save |
| 9 | Transaction/failure ordering | DB insert first (row w/o object = user-visible 502 on GET — don't manufacture spec's corruption state); MinIO cleanup inside the delete tx (commits after cleanup → violates spec's row-first) | Upload: **validate → MinIO put → DB insert**; DB failure → best-effort `eliminar` object + log (orphan invisible, no row ⇒ no API reference). Delete: **404 lookup → (ADMIN bypass \| client estado check → 403) → row delete (auto-commit) → MinIO remove with `onErrorResume` → log, still 204** — literal row-first; later GET → 404 even if MinIO down (spec scenario) |

## Data Flow

```
HTTP → DocumentoController (@AuthenticationPrincipal Jwt + Authorization header)
     → DocumentoService (local 400/413 → expediente gate → storage → repo)
         ├─ ExpedienteClient ──GET /api/expedientes/{id}──→ expedientes:8083 (JWT relay, 5s → 502/404)
         ├─ DocumentoStorage ──MinioAsyncClient─────────→ bucket "documentos" @ :9000, key {estudioId}/{uuid}.pdf
         └─ DocumentoRepository ────────────────────────→ PG foliox_documentos.documentos
errors → GlobalExceptionHandler / entry point → ApiError 400/401/403/404/413/502 (español)
```

## File Changes

| File | Action | Description |
|------|--------|-------------|
| `foliox-documentos/pom.xml` | Modify | + r2dbc-postgresql, security, oauth2-resource-server, validation, foliox-common, `io.minio:minio:8.6.0` (explicit) |
| `.../resources/application.yml` | Modify | r2dbc `foliox_documentos`, `jwk-set-uri: http://localhost:8081/oauth2/jwks`, `sql.init.mode: always`, codec+multipart `25MB`, `app.minio.*` (`${MINIO_ENDPOINT:http://localhost:9000}`, `${MINIO_ROOT_USER:foliox}`, `${MINIO_ROOT_PASSWORD:foliox12345}`, `${MINIO_DEFAULT_BUCKET:documentos}`), `app.expedientes.base-url: ${EXPEDIENTES_URL:http://localhost:8083}` |
| `.../resources/schema.sql` | Create | DDL below |
| `.../DocumentosApplication.java` | Modify | `@OpenAPIDefinition` + `@SecurityScheme(bearerAuth)` (expedientes copy) |
| `.../config/SecurityConfig.java` | Create | expedientes copy (public swagger paths, ApiError 401 entry point, rol→ROLE converter, CSRF off) |
| `.../config/MinioConfig.java` | Create | `MinioAsyncClient` bean from `app.minio.*` props |
| `.../config/WebClientConfig.java` | Create | Prototype-scoped `WebClient.Builder` bean — Boot 4.1 does not auto-register a `WebClient.Builder` in this app; declared explicitly (prototype by convention so injected builders are not shared/mutated). Added post-smoke (verify WARNING 2) |
| `.../documento/Documento.java` | Create | `@Table("documentos")`, Lombok getters/setters |
| `.../documento/DocumentoRepository.java` | Create | `findByEstudioId`, `findByIdAndEstudioId` (no filters per spec) |
| `.../documento/DocumentoService.java` | Create | 6 flows (decisions 5, 8, 9) |
| `.../documento/ActualizarDocumentoCommand.java` | Create | Nullable patch record |
| `.../documento/DocumentoResponse.java` | Create | `record (id, estudioId, expedienteId, nombre, ubicacion, creadoEn)` + `from()` |
| `.../storage/DocumentoStorage.java` | Create | `Mono<Void> guardar(clave, byte[])`, `Mono<InputStream> obtener(clave)` (empty → missing), `Mono<Void> eliminar(clave)` |
| `.../storage/MinioDocumentoStorage.java` | Create | Impl; boundedElastic boundary; NoSuchKey → empty |
| `.../client/ExpedienteClient.java` | Create | WebClient, header relay, 5s `.timeout()`, error mapping (decision 3) |
| `.../client/ExpedienteResumen.java` | Create | `record (UUID id, UUID estudioId, String estado)` |
| `.../exception/ArchivoNoValidoException.java` | Create | Non-PDF → 400 |
| `.../exception/ServicioNoDisponibleException.java` | Create | expedientes/MinIO failure → 502 |
| `.../web/DocumentoController.java` | Create | Six mappings, JWT helpers, `@RequestHeader(Authorization)` on POST/PATCH/DELETE |
| `.../web/GlobalExceptionHandler.java` | Create | expedientes copy + 413/502/400-archivo handlers |

20 files: **17 new, 3 modified, 0 deleted**. `foliox-expedientes` and `foliox-common`: **no changes**.

## Schema & Contracts

```sql
CREATE TABLE IF NOT EXISTS documentos (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    estudio_id UUID NOT NULL,           -- plain UUID, no cross-DB FK (task 8 precedent)
    expediente_id UUID NOT NULL,        -- API always sets it; spec never disassociates
    nombre VARCHAR(255) NOT NULL,       -- truncate service-side if >255
    ubicacion VARCHAR(255) NOT NULL,    -- MinIO key {estudioId}/{uuid}.pdf
    creado_en TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS ix_documentos_estudio_id ON documentos (estudio_id);
```

No `estado`/versión columns (belong to plantillas). Fresh DB → `CREATE IF NOT EXISTS` only, no backfill (unlike expedientes).

```java
record ActualizarDocumentoCommand(String nombre, UUID expedienteId);        // null = absent
record DocumentoResponse(UUID id, UUID estudioId, UUID expedienteId,
        String nombre, String ubicacion, Instant creadoEn);
record ExpedienteResumen(UUID id, UUID estudioId, String estado);           // estado compared as String
```

## Endpoints

| Verb | Path | Success |
|------|------|---------|
| POST | `/api/documentos` (`multipart/form-data`: `file` + `expedienteId`) | 201 + metadata |
| GET | `/api/documentos` | 200 list (estudio scope, metadata only) |
| GET | `/api/documentos/{id}` | 200 / 404 |
| GET | `/api/documentos/{id}/contenido` | 200 `application/pdf`, `Content-Disposition: attachment` |
| PATCH | `/api/documentos/{id}` (`nombre`, `expedienteId`) | 200 |
| DELETE | `/api/documentos/{id}` | 204 |

## Error mapping

| Trigger | Mechanism | Status |
|---------|-----------|--------|
| Missing/invalid JWT | SecurityConfig entry point (copy) | 401 |
| Missing `file`/`expedienteId`, bad UUID, undecodable body | framework exceptions → existing 400 handlers (bad `expedienteId` → `ServerWebInputException` from explicit `parseUuid`, fix `29e9cfe`) | 400 |
| Non-PDF content-type or magic bytes | `ArchivoNoValidoException` → new handler | 400 |
| Upload > cap (Content-Length or counted); non-file part overflow | `DataBufferLimitException` → new handler | 413 |
| Non-admin delete, expediente not `CERRADO` | `ForbiddenOperationException` | 403 |
| Unknown / cross-estudio id (local or upstream 404) | `ResourceNotFoundException` | 404 |
| expedientes timeout/refused/5xx; MinIO object missing/unreadable; MinIO down on upload | `ServicioNoDisponibleException` → new handler | 502 |

## Wiring Notes

Ports: **documentos 8084** ↔ **expedientes 8083** ↔ **auth 8081** (`/oauth2/jwks`); MinIO `localhost:9000` (console 9001), bucket `documentos` auto-created by compose; PG `foliox_documentos@5432`. springdoc: 6 endpoints + bearerAuth; POST `consumes = multipart/form-data` so Swagger UI renders file input + `expedienteId` field (verify operability — proposal risk).

## Testing Strategy

Deferred to task 23. Targets: unit — service flows with mocked repo/storage/client (404-before-403, check-before-side-effect ordering, cap counting, delete row-first); integration — WebTestClient × 22 scenarios incl. 413/502; OpenAPI — 6 bearerAuth endpoints, multipart operable.

## Migration / Rollout

Fresh additive table (DB exists, empty). Rollback = revert PR; optionally `DROP TABLE documentos` + empty bucket (disposable pre-launch).

## Open Questions

- [ ] Non-blocking: confirm `readInputStream` reads land on boundedElastic (not Netty loop) — verify with concurrent downloads in tasks/verify.
- [ ] Non-blocking: springdoc multipart operability for `FilePart` — verify at apply.
