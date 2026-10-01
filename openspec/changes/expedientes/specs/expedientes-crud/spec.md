# Delta for expedientes-crud

Enums (foliox-common): `fuero` ∈ {CIVIL, PENAL, LABORAL, ADMINISTRATIVO, FAMILIAS, COMERCIAL}; `estado` ∈ {INGRESADO, EN_TRAMITE, EN_SUBSANA, PARA_RESOLVER, CERRADO}, default INGRESADO.

## ADDED Requirements

### Requirement: Create expediente

`POST /api/expedientes` MUST create an expediente owned by the caller's estudio. `estudio_id` MUST come from the JWT claim, never the body, and a body-sent value MUST be ignored. `nombre` MUST be present (missing `nombre` → 400 with a Spanish message). `fuero`/`estado` MUST be valid. Every expediente response (201/200) MUST include `nombre`.

#### Scenario: Successful creation

- GIVEN an authenticated ABOGADO with `estudio_id` claim A
- WHEN POSTing valid data with `nombre: "Pérez c/ Estado"` and `fuero: CIVIL` and spoofed body `estudio_id` B
- THEN 201 with the created expediente echoing `nombre`, `estudio_id` A, `estado` INGRESADO

#### Scenario: Missing nombre rejected

- GIVEN an authenticated user
- WHEN POSTing valid data without `nombre`
- THEN 400 with `ApiError` and field error `el nombre es obligatorio`

#### Scenario: Invalid fuero rejected

- GIVEN an authenticated user
- WHEN POSTing an unknown `fuero`
- THEN 400 with `ApiError` and a Spanish message

### Requirement: List with optional filters

`GET /api/expedientes` MUST return only the caller's estudio expedientes. Optional `fuero`/`estado` query params MUST be AND-combined. An unknown enum value in a filter MUST yield 400.

#### Scenario: AND-combined filters

- GIVEN a user in estudio A with expedientes of mixed fuero/estado
- WHEN GETting `?fuero=CIVIL&estado=EN_TRAMITE`
- THEN 200 with only estudio-A rows matching both

#### Scenario: No filters

- GIVEN an authenticated user
- WHEN GETting `/api/expedientes`
- THEN 200 with all of the caller's estudio expedientes

### Requirement: Get by id

`GET /api/expedientes/{id}` MUST return 200 for the caller's estudio expediente, 404 when the id does not exist or belongs to another estudio.

#### Scenario: Own expediente

- GIVEN an expediente in the caller's estudio
- WHEN GETting its id
- THEN 200 with that expediente

#### Scenario: Cross-tenant id

- GIVEN an expediente of estudio B
- WHEN a user of estudio A GETs its id
- THEN 404

### Requirement: Partial update via PATCH

`PATCH /api/expedientes/{id}` MUST apply only the fields present in the body, respond 200, and allow `nombre`/`fuero`/`estado` changes without transition-graph validation. Unknown or cross-estudio id MUST yield 404; invalid enum MUST yield 400; `estudio_id` MUST NOT be modifiable.

#### Scenario: Partial field update

- GIVEN an expediente in EN_TRAMITE in the caller's estudio
- WHEN PATCHing `{"estado": "CERRADO"}`
- THEN 200 with `estado` CERRADO, other fields unchanged

#### Scenario: Rename via PATCH

- GIVEN an expediente in the caller's estudio
- WHEN PATCHing `{"nombre": "Renombrado"}`
- THEN 200 with `nombre` Renombrado, `fuero`/`estado` unchanged

#### Scenario: Cross-tenant PATCH

- GIVEN an expediente of another estudio
- WHEN a different-estudio user PATCHes its id
- THEN 404 and the resource is unchanged

### Requirement: Delete restricted to ADMIN

`DELETE /api/expedientes/{id}` MUST hard-delete and respond 204 only for `ROLE_ADMIN`. ABOGADO/ASISTENTE MUST receive 403. Non-existent or cross-estudio id MUST yield 404 regardless of role.

#### Scenario: ADMIN deletes own expediente

- GIVEN an ADMIN with an expediente in their estudio
- WHEN DELETEing it
- THEN 204, and a later GET returns 404

#### Scenario: ABOGADO forbidden

- GIVEN an ABOGADO with an expediente in their estudio
- WHEN DELETEing it
- THEN 403 with `ApiError`

#### Scenario: ADMIN cross-tenant delete

- GIVEN an ADMIN of estudio A targeting estudio B's expediente
- WHEN sending DELETE
- THEN 404

### Requirement: Authentication required

All endpoints MUST require a valid JWT; missing/invalid token MUST yield 401 with `ApiError`. Any authenticated role of the caller's estudio MAY create, list, get, update.

#### Scenario: Missing token

- GIVEN no Authorization header
- WHEN calling `GET /api/expedientes`
- THEN 401

#### Scenario: ASISTENTE can create

- GIVEN an authenticated ASISTENTE of estudio A
- WHEN POSTing a valid expediente
- THEN 201

### Requirement: Error responses use ApiError

All 400/401/403/404 responses MUST use the shared `ApiError` shape with Spanish messages.

#### Scenario: Malformed body

- GIVEN an authenticated user
- WHEN POSTing an invalid body
- THEN 400 with `ApiError` and a Spanish message

### Requirement: OpenAPI documentation

springdoc MUST expose the five endpoints under `/api/expedientes` with bearerAuth; `fuero`/`estado` query params and `{id}` path param MUST be visible in OpenAPI.

#### Scenario: Swagger visibility

- GIVEN the service is running
- WHEN opening the OpenAPI UI
- THEN five endpoints appear with bearerAuth and parameters
