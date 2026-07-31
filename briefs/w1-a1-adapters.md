# Session brief: w1-a1-adapters

Open a Claude Code session with cwd `../w1-a1-adapters` and paste everything below the line.

---

You are worker `w1-a1-adapters`, one of six running in parallel on isolated git worktrees. You are on branch `track/w1-a1-adapters`. Other workers cannot see your changes and you cannot see theirs — that is intentional.

Read these three files at the repo root before touching anything: `CLAUDE.md` (architecture and commands), `CONTRACTS.md` (C1–C9, frozen and non-negotiable), `WORKPLAN.md` sections 7–11 (acceptance matrix, validation ladder, partition map).

The Maven project is in `java-assignment/`. Run all mvnw commands from there.

## Your lane — touch nothing else

```
java-assignment/src/main/java/com/fulfilment/application/monolith/location/LocationGateway.java
java-assignment/src/main/java/com/fulfilment/application/monolith/warehouses/adapters/database/**
java-assignment/src/test/java/com/fulfilment/application/monolith/location/**
java-assignment/src/test/java/com/fulfilment/application/monolith/warehouses/adapters/database/**
```

Frozen zone, owned by nobody: `pom.xml`, `application.properties`, `import.sql`, `openapi/warehouse-openapi.yaml`, `warehouses/domain/ports/**`, `warehouses/domain/models/**`, `CLAUDE.md`, `CONTRACTS.md`, `WORKPLAN.md`, anything under `target/`. No new Maven dependencies (C9). If you think you must edit outside your lane, don't — report it instead.

## Your job

1. Implement `LocationGateway.resolveByIdentifier`: return the matching `Location`, or `null` for an unknown identifier. Add `@ApplicationScoped` (contract C6) so it injects as a `LocationResolver`.
2. Implement the four unimplemented `WarehouseRepository` methods and fix `getAll()`:
   - `getAll()` excludes archived rows — `archivedAt IS NULL` (C1)
   - `findByBusinessUnitCode` returns the **active** row only, `null` when absent (C2)
   - `create()` sets `createdAt`
   - `update()` persists changes to the matching `DbWarehouse` row **including `archivedAt`**, so the archive flow works
   - `remove()` is a real hard delete but is unused by the archive flow (C7)
   - add domain → `DbWarehouse` mapping alongside the existing `toWarehouse()`
   - writing methods need `jakarta.transaction.Transactional`

## Acceptance rows you own

| Row | Assertion |
|---|---|
| L1 | `resolveByIdentifier("ZWOLLE-001")` returns a Location with that identification |
| L2 | unknown identifier (`"NOWHERE-999"`) returns null |
| W11 | an archived warehouse row still exists with `archivedAt` set (history preserved) |
| W12 | `getAll()` excludes archived warehouses |

L1/L2 are plain JUnit — uncomment and complete the existing stub in `LocationGatewayTest`, no Quarkus boot. W11/W12 need persistence, so use `@QuarkusTest` in a new `WarehouseRepositoryTest`. Use `@TestTransaction` or your own rows; don't mutate seeded `MWH.001`/`MWH.012`/`MWH.023` in a way that leaks into other tests.

## Verify, then commit

```bash
cd java-assignment && ./mvnw -q compile && ./mvnw test -Dtest=LocationGatewayTest && ./mvnw test -Dtest=WarehouseRepositoryTest
```

Confirm you stayed in your lane: `git diff --name-only master`

```bash
git add -A && git commit --author="iznika <solutions@attrilio.com>" -m "feat(warehouse-db): implement location resolver and warehouse persistence adapter"
```

No `Co-Authored-By` or attribution trailer — firm user preference. Verify with `git log -1 --pretty=format:"%(trailers)"`, which must print `[]`.

Report honestly: branch, commit sha, files changed, per-row status, literal test output, any lane violations, any contract problems for the aggregator. A truthful red beats an optimistic green.
