# Tasks: Expedientes Service (Task 8)

Each task = one buildable/verifiable slice. Tests deferred to task 23.

## Phase 1: Foundation

- [x] 1.1 **Deps + runtime config** — Files: `foliox-expedientes/pom.xml` (usuarios deps minus `spring-security-crypto`), `src/main/resources/application.yml` (port 8083, r2dbc URL `foliox_expedientes`, `jwk-set-uri: http://localhost:8081/oauth2/jwks`, `sql.init.mode: always`) — Verify: `mvn -pl foliox-expedientes -am compile` — ~30 lines
- [x] 1.2 **Schema bootstrap** — File: `foliox-expedientes/src/main/resources/schema.sql`: DDL + `ix_expedientes_estudio_id` per design (String enums, `estado` default `INGRESADO`, no cross-DB FK) — Verify: compiles into jar; applied at boot in 4.1 — ~10 lines
- [x] 1.3 **OpenAPI annotations** — File: `.../expedientes/ExpedientesApplication.java`: add `@OpenAPIDefinition` + `@SecurityScheme(bearerAuth)` — Verify: compile; UI check in 4.1 — ~10 lines

## Phase 2: Domain core

- [x] 2.1 **Command/response records** — Files: `expediente/CrearExpedienteCommand.java` (`@NotNull` fuero, Spanish message; optional estado), `ActualizarExpedienteCommand.java` (all-nullable, no estudio_id), `ExpedienteResponse.java` (`from()` mapper, includes estudioId) — Verify: compile — ~40 lines
- [x] 2.2 **Entity + repository** — Files: `expediente/Expediente.java` (`@Table("expedientes")`, Lombok, String enum columns), `expediente/ExpedienteRepository.java` (5 derived queries per design) — Verify: compile — ~35 lines
- [x] 2.3 **Service: tenancy + 404→403** — File: `expediente/ExpedienteService.java`: `crear` (estado default, `creadoEn=now`), `listar` (pick derived method, AND filters), `obtener`/`actualizar` (scoped lookup else `ResourceNotFoundException`; PATCH non-null only, no transition graph), `eliminar` (scoped **404 before** `ForbiddenOperationException` 403, hard delete) — Verify: compile — ~110 lines
- [x] 2.4 **nombre field** — Add required `nombre` to CrearExpedienteCommand/Expediente/ExpedienteResponse/ActualizarExpedienteCommand + schema.sql (`ADD COLUMN IF NOT EXISTS` + backfill, spec/design deltas) — Verify: rebuild + smoke 4/4 (201 echo, 400 sin nombre, lista backfill, PATCH renombra) — ~40 lines

## Phase 3: Web/security wiring

- [x] 3.1 **SecurityConfig + 401 ApiError** — File: `config/SecurityConfig.java`: usuarios copy minus `POST /api/estudios` permitAll, plus custom `authenticationEntryPoint` → ApiError 401 en español (decision 4) — Verify: compile — ~65 lines
- [x] 3.2 **GlobalExceptionHandler** — File: `web/GlobalExceptionHandler.java`: usuarios handlers + new `ServerWebInputException` / `MethodArgumentTypeMismatchException` → 400 ApiError español (bad enum filter, bad UUID, undecodable body) — Verify: compile — ~105 lines
- [x] 3.3 **Controller: five endpoints** — File: `web/ExpedienteController.java`: `@Tag`/`@SecurityRequirement`, POST 201, GET list (typed enum params), GET/PATCH/DELETE by id, private `estudioId(Jwt)`/`isAdmin(Jwt)` helpers, JWT-only tenancy (body `estudio_id` ignored) — Verify: compile — ~85 lines

## Phase 4: Boot + smoke verification

- [x] 4.1 **Boot on 8083** — Files: none (runtime) — Verify: app starts, schema applied, OpenAPI UI shows the five `/api/expedientes` endpoints with bearerAuth + fuero/estado/{id} params
- [x] 4.2 **Auth/create/list/get smoke** — Verify: no token → 401 ApiError; POST with spoofed body estudio_id → 201 with JWT estudio, estado INGRESADO; unknown fuero → 400 Spanish; `?fuero=CIVIL&estado=EN_TRAMITE` AND-combined; GET by id: own 200, cross-estudio 404
- [x] 4.3 **PATCH/delete smoke** — Verify: PATCH `{"estado":"CERRADO"}` → 200, other fields unchanged; cross-estudio PATCH → 404 unchanged; DELETE ADMIN → 204 then GET 404; ABOGADO/ASISTENTE → 403 ApiError; ADMIN cross-estudio → 404

## Review Workload Forecast

Estimated changed lines: 490
Chained PRs recommended: Yes
400-line budget risk: High
Decision needed before apply: Yes
