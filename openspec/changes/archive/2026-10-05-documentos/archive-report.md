# Archive Report — documentos (task 9)

**Change**: documentos · **Mode**: hybrid (openspec files + engram) · **Date**: 2026-10-05
**Archived to**: `openspec/changes/archive/2026-10-05-documentos/`
**Verify verdict**: PASS WITH WARNINGS — 0 CRITICAL / 2 WARNING / 2 SUGGESTION (ready to archive after orchestrator acknowledged both warnings)

## Source-of-truth sync (Step 2)

| Domain | Action | Details |
|--------|--------|---------|
| `documentos-crud` | **Created** (main spec did not exist — delta was a full spec, copied verbatim) | `openspec/changes/documentos/specs/documentos-crud/spec.md` → `openspec/specs/documentos-crud/spec.md` — 9 requirements / 22 scenarios |

No `openspec/config.yaml` exists → no `rules.archive` to apply. No requirements were merged, modified, or removed (non-destructive).

## Warning resolutions performed during archive

### 1. Design doc drift (verify WARNING 2 / SUGGESTION 1) — RESOLVED in `design.md`
- Added `.../config/WebClientConfig.java` to the **File Changes** table: prototype-scoped `WebClient.Builder` bean (Boot 4.1 does not auto-register one in this app; declared explicitly so injected builders are not shared/mutated).
- File count updated: **19 → 20 files** (16 → 17 new, 3 modified, 0 deleted).
- Documented the **`expedienteId` String-bind + explicit UUID parse fix** (decision 5 + error-mapping table): controller binds `@RequestPart("expedienteId") String` and parses via `DocumentoController.parseUuid` → `UUID.fromString` (commit `29e9cfe`). Direct `UUID` part binding rejected multipart parts arriving **without a part `Content-Type` with 415**; invalid UUID now throws `ServerWebInputException` → existing 400 handler → Spanish `ApiError`. **Semantics unchanged from the spec (still 400)** — spec-compliant correction, not a behavior change.

### 2. Apply-progress evidence gap (verify WARNING 1) — NOTED as follow-up (not fixable at archive)
Engram **#133** (`sdd/documentos/apply-progress`, 6 revisions) retains only the 4.3 checklist plus the aggregate claim "FASE 4 ENTERA HECHA"; per-check runtime evidence for smoke **4.1/4.2 was overwritten** by the 4.3 upsert and is not retrievable. Mitigated by verify: 6 live spot checks (scenarios 2, 4, 5, 18, 21, 22) + full code review; remaining `pass*` rows rest on task checkboxes + aggregate claim.

> **FOLLOW-UP (candidate task 23 — tests)**: when task 23 lands, encode smoke 4.1/4.2 scenarios (**1, 3, 6–10, 19, 22-partial**) as automated integration tests so this evidence lives in code, not in a revision-prone engram note (verify SUGGESTION 2).

## Engram traceability (observation IDs)

| Artifact | Topic key | Observation ID |
|----------|-----------|----------------|
| Apply progress | `sdd/documentos/apply-progress` | **#133** (6 revisions) |
| Verify report | `sdd/documentos/verify-report` | **#138** |
| Archive report | `sdd/documentos/archive-report` | this artifact (upsert) |

## Archive contents (Step 4 verification)

- `proposal.md` ✅
- `specs/documentos-crud/spec.md` ✅ (9 requirements / 22 scenarios)
- `design.md` ✅ (D1–D9 coherent; 2 drift warnings fixed during archive)
- `tasks.md` ✅ **14/14 tasks complete** — 1.1–1.2, 2.1–2.6, 3.1–3.3, 4.1–4.3 all `[x]`, 0 unchecked (verify-report's "12/12" enumerates these same 14 items — miscount, file is authoritative)
- `verify-report.md` ✅ (PASS WITH WARNINGS, 22/22 scenarios, 0 CRITICAL)
- `archive-report.md` ✅ (this file)

## Status

**SDD cycle complete** — planned, implemented, verified, archived. Follow-up for next change: task 23 (automated tests) covering the `pass*` scenarios listed above.
