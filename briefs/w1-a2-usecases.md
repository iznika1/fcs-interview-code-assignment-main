# Session brief: w1-a2-usecases

Open a Claude Code session with cwd `../w1-a2-usecases` and paste everything below the line.

---

You are worker `w1-a2-usecases`, one of six running in parallel on isolated git worktrees. You are on branch `track/w1-a2-usecases`. Other workers cannot see your changes and you cannot see theirs — that is intentional.

Read these three files at the repo root before touching anything: `CLAUDE.md` (architecture and commands), `CONTRACTS.md` (C1–C9, frozen and non-negotiable), `WORKPLAN.md` sections 7–11 (acceptance matrix, validation ladder, partition map).

The Maven project is in `java-assignment/`. Run all mvnw commands from there.

## Your lane — touch nothing else

```
java-assignment/src/main/java/com/fulfilment/application/monolith/warehouses/domain/usecases/**
java-assignment/src/test/java/com/fulfilment/application/monolith/warehouses/domain/usecases/**
```

Frozen zone, owned by nobody: `pom.xml`, `application.properties`, `import.sql`, `openapi/warehouse-openapi.yaml`, `warehouses/domain/ports/**`, `warehouses/domain/models/**`, `CLAUDE.md`, `CONTRACTS.md`, `WORKPLAN.md`, anything under `target/`. No new Maven dependencies (C9).

Extending the use-case classes' own constructors to inject `LocationResolver` **is** inside your lane and is expected.

## Your job

**`CreateWarehouseUseCase.create`** — throw `WarehouseValidationException` (C5) with a clear message on each violation:
- the business unit code is not already used by an active warehouse
- the location exists (resolve via the `LocationResolver` port)
- the location has not reached `maxNumberOfWarehouses`
- new capacity + capacity of existing active warehouses at that location ≤ the location's `maxCapacity`
- stock ≤ capacity

Set `createdAt` if unset, then call `warehouseStore.create`.

**`ReplaceWarehouseUseCase.replace`** —
- the previous active warehouse with that business unit code must exist, else throw `WarehouseNotFoundException` (C5)
- new capacity must hold the previous warehouse's stock
- new stock must equal the previous warehouse's stock
- archive the previous warehouse (set `archivedAt`, call `update`), then create the new one reusing the same business unit code, preserving history
- the new warehouse must still satisfy the location rules — but the slot freed by archiving the old one counts, so don't spuriously fail the count/capacity check against the warehouse being replaced. Explain your handling in your report.

**`ArchiveWarehouseUseCase.archive`** — set `archivedAt`, call `warehouseStore.update`. Soft delete; never call `remove()` (C7).

## Hexagonal boundary — the thing you will be graded on

This package must **not** import `jakarta.ws.rs` or `jakarta.persistence`, and must never throw `WebApplicationException`. Self-check before committing:

```bash
grep -rn "jakarta.ws.rs\|jakarta.persistence" src/main/java/com/fulfilment/application/monolith/warehouses/domain/
```

Any hit is a failure.

## Acceptance rows you own

| Row | Assertion |
|---|---|
| W2 | duplicate active business unit code → validation error |
| W3 | non-existent location → validation error |
| W4 | location at max warehouse count → validation error |
| W5 | capacity exceeds the location's max capacity → validation error |
| W6 | stock greater than capacity → validation error |
| W13 | replace archives the old warehouse and creates a new active one with the same code |
| W14 | new capacity cannot hold the previous stock → validation error |
| W15 | new stock ≠ previous stock → validation error |
| W16 | replace an unknown business unit code → not found error |

Write these as **plain JUnit with hand-written in-memory fakes** for `WarehouseStore` and `LocationResolver`. No Quarkus boot, no Mockito, no new dependencies (C9) — your tests must run in milliseconds. Use the location table at the bottom of `CONTRACTS.md` for fixtures. Per C8, seeded `MWH.001` already violates the capacity rule and is grandfathered — build your own fake data rather than relying on `import.sql`.

## Verify, then commit

```bash
cd java-assignment && ./mvnw -q compile && ./mvnw test -Dtest=CreateWarehouseUseCaseTest,ReplaceWarehouseUseCaseTest,ArchiveWarehouseUseCaseTest
```

Confirm you stayed in your lane: `git diff --name-only master`

```bash
git add -A && git commit --author="iznika <solutions@attrilio.com>" -m "feat(warehouse-domain): enforce warehouse creation, replacement and archive rules"
```

No `Co-Authored-By` or attribution trailer — firm user preference. Verify with `git log -1 --pretty=format:"%(trailers)"`, which must print `[]`.

Report honestly: branch, commit sha, files changed, per-row status, literal test output, any lane violations, any contract problems for the aggregator. A truthful red beats an optimistic green.
