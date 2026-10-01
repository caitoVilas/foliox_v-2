# Design: Expedientes Service (Task 8)

## Technical Approach

Build the `foliox-expedientes` slice mirroring `foliox-usuarios` (entity → repository → service → controller) over table `expedientes` in DB `foliox_expedientes`. Tenancy comes only from JWT claim `estudio_id`; enums validated at the HTTP boundary. Implements all `expedientes-crud` requirements. Verified in `spring-boot-r2dbc` 4.1.0 sources: `R2dbcInitializationAutoConfiguration` runs `schema.sql` via `ConnectionFactory`, so `spring.sql.init.mode: always` needs no JDBC driver — locked bootstrap holds.

## Architecture Decisions

| # | Decision | Rejected option (tradeoff) | Choice + rationale |
|---|----------|---------------------------|--------------------|
| 1 | DELETE authorization | `@PreAuthorize("hasRole('ADMIN')")` — 403 fires before lookup, violating "cross-tenant → 404 regardless of role" | Service-level check: scoped lookup (404) first, then role (403). No `@PreAuthorize` anywhere |
| 2 | Enum storage | Entity enum fields — r2dbc-postgresql enum codec uncertainty | `String` columns (mirror `Usuario.rol`); convert at DTO/service boundary |
| 3 | Filtered list | One `@Query` with nullable params — r2dbc null-param typing quirks | 4 derived query methods; service picks by which filters present (AND) |
| 4 | 401 + bad input | Default 401 (no body), English Jackson messages | Custom `authenticationEntryPoint` → ApiError 401; handlers for `ServerWebInputException` + `MethodArgumentTypeMismatchException` → 400 ApiError en español |
| 5 | `creado_en` value | DB default only — stays `null` in the 201 response | Service sets `Instant.now()` on create; DB default kept as guard |
| 6 | Bootstrap | Flyway | `schema.sql` + `spring.sql.init.mode: always` (locked), verified R2DBC initializer |

## Data Flow

```
HTTP → ExpedienteController (@AuthenticationPrincipal Jwt → estudioId / isAdmin)
     → ExpedienteService (tenant scope, 404→403 precedence, enum mapping)
     → ExpedienteRepository → PG foliox_expedientes.expedientes
errors → GlobalExceptionHandler / entry point → ApiError 400/401/403/404 (español)
```

## File Changes

| File | Action | Description |
|------|--------|-------------|
| `foliox-expedientes/pom.xml` | Modify | Add usuarios deps except `spring-security-crypto` |
| `.../resources/application.yml` | Modify | r2dbc URL `foliox_expedientes`, `jwk-set-uri: http://localhost:8081/oauth2/jwks`, `sql.init.mode: always`; port **8083** (8080-8082/8084-8086 taken) |
| `.../resources/schema.sql` | Create | DDL below |
| `.../expedientes/ExpedientesApplication.java` | Modify | Add `@OpenAPIDefinition` + `@SecurityScheme(bearerAuth)` |
| `.../config/SecurityConfig.java` | Create | usuarios copy minus `POST /api/estudios` permitAll, plus decision-4 entry point |
| `.../expediente/Expediente.java` | Create | `@Table("expedientes")`, Lombok `@Getter/@Setter` |
| `.../expediente/ExpedienteRepository.java` | Create | Derived queries (below) |
| `.../expediente/ExpedienteService.java` | Create | Business flow (below) |
| `.../expediente/CrearExpedienteCommand.java` | Create | Validated request record |
| `.../expediente/ActualizarExpedienteCommand.java` | Create | All-nullable patch record |
| `.../expediente/ExpedienteResponse.java` | Create | Response record with `from()` mapper |
| `.../web/ExpedienteController.java` | Create | Five mappings, JWT helpers |
| `.../web/GlobalExceptionHandler.java` | Create | usuarios copy + decision-4 handlers |

## Schema & Contracts

```sql
id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
estudio_id UUID NOT NULL,        -- plain UUID, no cross-DB FK
nombre VARCHAR(255),             -- required at API level (400 si falta); backfilled 'Expediente'
fuero VARCHAR(20) NOT NULL,      -- Fuero.name()
estado VARCHAR(20) NOT NULL DEFAULT 'INGRESADO',
creado_en TIMESTAMPTZ NOT NULL DEFAULT now();
CREATE INDEX ix_expedientes_estudio_id ON expedientes (estudio_id);
```

`schema.sql` is idempotent for existing DBs (table already has data): `CREATE TABLE IF NOT EXISTS ... nombre VARCHAR(255)` for fresh DBs, then `ALTER TABLE expedientes ADD COLUMN IF NOT EXISTS nombre VARCHAR(255);` and `UPDATE expedientes SET nombre = 'Expediente' WHERE nombre IS NULL;` — no data loss.

```java
record CrearExpedienteCommand(@NotNull(message="el nombre es obligatorio") String nombre,
        @NotNull(message="el fuero es obligatorio") Fuero fuero,
        EstadoExpediente estado);                              // optional → INGRESADO
record ActualizarExpedienteCommand(String nombre, Fuero fuero, EstadoExpediente estado); // nullable PATCH, no estudio_id field
record ExpedienteResponse(UUID id, UUID estudioId, String nombre, Fuero fuero,
        EstadoExpediente estado, Instant creadoEn);            // estudioId required by spec
```

Spoofed body `estudio_id` = unknown property → ignored (Boot `FAIL_ON_UNKNOWN_PROPERTIES=false`); tenancy from JWT only.

**Repository**: `findByEstudioId(e)`, `findByIdAndEstudioId(id, e)`, `findByEstudioIdAndFuero`, `findByEstudioIdAndEstado`, `findByEstudioIdAndFueroAndEstado`.

**Service** (`@Transactional` on writes, mirror usuarios): `crear` (nombre required, estado default, creadoEn=now, save); `listar(e, fuero?, estado?)` (pick derived method); `obtener`/`actualizar` → scoped lookup else `ResourceNotFoundException("Expediente no encontrado")`; PATCH applies only non-null fields, no transition graph; `eliminar(id, e, admin)` → scoped **404 before** `ForbiddenOperationException` 403, else hard delete.

**Controller** (`@Tag`, `@SecurityRequirement`, JSON, private `estudioId(Jwt)`/`isAdmin(Jwt)` helpers):

| Verb | Path | Success |
|------|------|---------|
| POST | `/api/expedientes` | 201 |
| GET | `/api/expedientes?fuero=&estado=` (typed enum params) | 200 |
| GET | `/api/expedientes/{id}` | 200 |
| PATCH | `/api/expedientes/{id}` | 200 |
| DELETE | `/api/expedientes/{id}` | 204 |

## Error mapping

| Trigger | Mechanism | Status |
|---------|-----------|--------|
| Missing/invalid JWT | custom `authenticationEntryPoint` in SecurityConfig | 401 |
| Body validation/bind | existing handlers (mirror usuarios) | 400 |
| Bad enum filter, undecodable body, bad UUID | new `ServerWebInputException` / `MethodArgumentTypeMismatchException` handlers | 400 |
| Non-admin delete, own tenant | `ForbiddenOperationException` | 403 |
| Unknown / cross-tenant id | `ResourceNotFoundException` | 404 |

## Testing Strategy

Deferred to task 23. Targets: unit — tenant scoping + 404-before-403 precedence (StepVerifier, mocked repo); integration — WebTestClient per spec scenario; OpenAPI — five bearerAuth endpoints.

## Migration / Rollout

Additive. `nombre` column added idempotently (`ADD COLUMN IF NOT EXISTS`) + backfill `'Expediente'` for pre-existing rows — running DB keeps its test data. Rollback = revert PR (column left in place is harmless); drop only with `DROP TABLE expedientes`.

## Deviation Notes

1. No `@PreAuthorize` on DELETE (usuarios uses it) — spec precedence needs lookup-then-role (decision 1).
2. Beyond usuarios: 401 ApiError entry point + two 400 handlers — spec mandates ApiError + Spanish for 400/401/403/404.
3. `creadoEn` application-side (decision 5).

## Open Questions

- None.
