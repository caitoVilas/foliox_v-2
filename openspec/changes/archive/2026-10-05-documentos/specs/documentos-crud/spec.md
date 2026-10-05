# Delta for documentos-crud

Fields (spects.txt): `id`, `estudio_id`, `expediente_id`, `nombre`, `ubicacion`, `creado_en`. No new enums (version/activo belong to plantillas). Cross-cutting tenancy: `estudio_id` MUST derive only from the JWT claim — a body-sent value MUST be ignored, a spoofed `expediente_id` MUST fail via JWT-relayed validation, and cross-tenant ids MUST yield 404 before any role check.

## ADDED Requirements

### Requirement: Upload PDF

`POST /api/documentos` MUST accept multipart (`file` + `expedienteId`) and create a document owned by the caller's estudio. PDF only: `application/pdf` content-type AND `%PDF-` magic bytes, else 400; missing `expedienteId` → 400; size cap exceeded → 413. `expedienteId` MUST be validated through expedientes with the caller's JWT relayed (nonexistent/cross-estudio → 404 propagates). MinIO key MUST be `{estudioId}/{uuid}.pdf`. 201 MUST echo metadata (`id`, `estudioId`, `expedienteId`, `nombre`, `ubicacion`, `creadoEn`).

#### Scenario: Successful upload

- GIVEN an authenticated ABOGADO of estudio A with an expediente of A and spoofed body `estudio_id` B
- WHEN POSTing a valid PDF with that `expedienteId`
- THEN 201 echoing metadata with `estudioId` A
- AND the object exists at `A/{uuid}.pdf`

#### Scenario: Non-PDF rejected

- GIVEN an authenticated user
- WHEN POSTing a file with `text/plain` content-type or bytes without `%PDF-`
- THEN 400 with `ApiError` and a Spanish message

#### Scenario: Oversized upload rejected

- GIVEN the configured size cap
- WHEN POSTing a PDF larger than the cap
- THEN 413 with `ApiError`

#### Scenario: Unknown or foreign expediente

- GIVEN an authenticated user of estudio A
- WHEN POSTing with an `expedienteId` that does not exist or belongs to estudio B
- THEN 404 propagated from expedientes
- AND no row or object is created

### Requirement: List and get metadata

`GET /api/documentos` MUST return only the caller's estudio documents as metadata (no content). `GET /api/documentos/{id}` MUST return 200 for the caller's estudio, 404 when the id does not exist or belongs to another estudio. The source spec defines no query filters for documentos.

#### Scenario: Tenant-scoped list

- GIVEN a user in estudio A with documents, plus documents of estudio B
- WHEN GETting `/api/documentos`
- THEN 200 with only estudio-A rows' metadata

#### Scenario: Own document metadata

- GIVEN a document in the caller's estudio
- WHEN GETting its id
- THEN 200 with that document's metadata

#### Scenario: Cross-tenant id

- GIVEN a document of estudio B
- WHEN a user of estudio A GETs its id
- THEN 404

### Requirement: Get content streaming

`GET /api/documentos/{id}/contenido` MUST stream the stored bytes as `application/pdf` with `Content-Disposition: attachment` (filename = `nombre`). Unknown or cross-estudio id MUST yield 404. If the row exists but the object is missing or unreadable in MinIO, the service MUST respond 502 with a Spanish `ApiError`: the row proves the resource exists for the tenant (404 is reserved for lookup misses) and storage is the failing dependency.

#### Scenario: Download own content

- GIVEN a document with stored content in the caller's estudio
- WHEN GETting `/api/documentos/{id}/contenido`
- THEN 200 with `application/pdf`, `Content-Disposition: attachment`, and the stored bytes

#### Scenario: Cross-tenant content

- GIVEN a document of estudio B
- WHEN a user of estudio A GETs its content
- THEN 404

#### Scenario: Object missing in MinIO

- GIVEN a row exists but its object is absent from MinIO
- WHEN the owner GETs the content
- THEN 502 with `ApiError` and a Spanish message

### Requirement: Partial update via PATCH

`PATCH /api/documentos/{id}` MUST apply only the fields present in the body (`nombre`, `expedienteId`) and respond 200. Re-association MUST re-run expediente validation (nonexistent/cross-estudio → 404; expedientes unavailable → 502). Unknown or cross-estudio id MUST yield 404 before any validation. `estudio_id` MUST NOT be modifiable.

#### Scenario: Rename and re-associate

- GIVEN a document in the caller's estudio
- WHEN PATCHing `{"nombre": "Contrato", "expedienteId": E2}` with E2 in the caller's estudio
- THEN 200 with both fields updated and `estudioId` unchanged

#### Scenario: Re-associate to foreign expediente

- GIVEN a document in the caller's estudio
- WHEN PATCHing `expedienteId` of an expediente owned by estudio B
- THEN 404 and the document is unchanged

#### Scenario: Cross-tenant PATCH

- GIVEN a document of estudio B
- WHEN a user of estudio A PATCHes `{"nombre": "Renombrado"}`
- THEN 404 and the document is unchanged

### Requirement: Delete gated by role and expediente estado

`DELETE /api/documentos/{id}` MUST first resolve the id within the caller's estudio (unknown/cross-estudio → 404, before any role check). `ROLE_ADMIN` MUST receive 204 without consulting expedientes. ABOGADO/ASISTENTE MUST be allowed only when the associated expediente's live estado is `CERRADO`, else 403 with `ApiError`. Deletion MUST hard-delete the row first, then the MinIO object; a cleanup failure MUST be logged and MUST NOT fail the request (still 204).

#### Scenario: ADMIN delete with MinIO unreachable

- GIVEN an ADMIN's document and MinIO stopped
- WHEN DELETEing it
- THEN 204 and a later GET returns 404
- AND the MinIO cleanup failure is logged without failing the request

#### Scenario: ABOGADO deletes document of closed expediente

- GIVEN an ABOGADO with a document whose expediente is CERRADO
- WHEN DELETEing it
- THEN 204 and a later GET returns 404

#### Scenario: ABOGADO forbidden on open expediente

- GIVEN an ABOGADO with a document whose expediente is EN_TRAMITE
- WHEN DELETEing it
- THEN 403 with `ApiError` and a Spanish message
- AND the document still exists

#### Scenario: Cross-tenant delete before role check

- GIVEN a document of estudio B whose expediente is EN_TRAMITE
- WHEN an ABOGADO of estudio A DELETEs its id
- THEN 404, not 403 (scoped lookup precedes role/estado evaluation)

### Requirement: Authentication required

All endpoints MUST require a valid JWT; missing/invalid token MUST yield 401 with `ApiError`. spects.txt restricts roles only for deletion: any authenticated role of the caller's estudio, including ASISTENTE, MAY upload, list, get, and update.

#### Scenario: Missing token

- GIVEN no Authorization header
- WHEN calling `GET /api/documentos`
- THEN 401

#### Scenario: ASISTENTE can upload

- GIVEN an authenticated ASISTENTE of estudio A with an expediente of A
- WHEN POSTing a valid PDF
- THEN 201

### Requirement: Inter-service failure semantics

Any expedientes call failure (connection refused, ~5s timeout) MUST map to 502 with a Spanish `ApiError`, and the operation MUST NOT proceed — never fail open. This applies to upload validation, PATCH re-association, and the non-admin delete estado check.

#### Scenario: Expedientes down during non-admin delete

- GIVEN the expedientes service is stopped and an ABOGADO's document in the caller's estudio
- WHEN DELETEing it
- THEN 502 with `ApiError` and a Spanish message
- AND a later GET returns 200 (document not deleted)

### Requirement: Error responses use ApiError

All 400/401/403/404/413/502 responses MUST use the shared `ApiError` shape with Spanish messages.

#### Scenario: Multipart without file

- GIVEN an authenticated user
- WHEN POSTing a multipart request without the `file` part
- THEN 400 with `ApiError` and a Spanish message

### Requirement: OpenAPI documentation

springdoc MUST expose the six endpoints under `/api/documentos` with bearerAuth; the POST multipart fields `file` and `expedienteId` MUST appear and be operable from the OpenAPI UI.

#### Scenario: Swagger visibility

- GIVEN the service is running
- WHEN opening the OpenAPI UI
- THEN six endpoints appear with bearerAuth
- AND the POST request accepts an uploadable multipart body

**Counts**: 9 requirements / 22 scenarios.
