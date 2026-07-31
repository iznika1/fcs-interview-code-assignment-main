# Session brief: w1-a3-restapi

Open a Claude Code session with cwd `../w1-a3-restapi` and paste everything below the line.

---

You are worker `w1-a3-restapi`, one of six running in parallel on isolated git worktrees. You are on branch `feat/warehouse-api`. Other workers cannot see your changes and you cannot see theirs — that is intentional.

Read these three files at the repo root before touching anything: `CLAUDE.md` (architecture and commands), `CONTRACTS.md` (C1–C9, frozen and non-negotiable), `WORKPLAN.md` sections 7–11 (acceptance matrix, validation ladder, partition map).

The Maven project is in `java-assignment/`. Run all mvnw commands from there.

## Your lane — touch nothing else

```
java-assignment/src/main/java/com/fulfilment/application/monolith/warehouses/adapters/restapi/**
java-assignment/src/test/java/com/fulfilment/application/monolith/warehouses/adapters/restapi/**
```

Frozen zone, owned by nobody: `pom.xml`, `application.properties`, `import.sql`, `openapi/warehouse-openapi.yaml`, `warehouses/domain/ports/**`, `warehouses/domain/models/**`, `CLAUDE.md`, `CONTRACTS.md`, `WORKPLAN.md`, anything under `target/`. No new Maven dependencies (C9). `ProductResource` and `StoreResource` are **outside** your lane — do not modify them.

## Your job

1. Implement the four unimplemented `WarehouseResourceImpl` methods by delegating to the inbound ports (`CreateWarehouseOperation`, `ReplaceWarehouseOperation`, `ArchiveWarehouseOperation`) and `WarehouseStore` for reads. Those interfaces already exist and are frozen — inject them.

   Per **contract C4**, `{id}` in `/warehouse/{id}` means the **business unit code**, not the database primary key, for both GET and DELETE.

   The workers implementing those ports are running concurrently, so their implementations are absent from your worktree. Code against the interfaces — that is exactly the point of the ports.

2. Add an `ExceptionMapper` **in the adapter layer** (never in the domain) translating `WarehouseValidationException` → 400 and `WarehouseNotFoundException` → 404. Match the JSON error shape used by the existing `ErrorMapper` classes in `ProductResource`/`StoreResource` so responses stay consistent. Archive returns 204 with no body.

3. Update `WarehouseEndpointIT`: uncomment the assertions in `testSimpleCheckingArchivingWarehouses` and change `delete("warehouse/1")` to `delete("warehouse/MWH.001")` per C4.

## Generated code

`WarehouseResource` and `com.warehouse.api.beans.Warehouse` are **generated** from `src/main/resources/openapi/warehouse-openapi.yaml` into `target/`. Never hand-edit anything in `target/`, and never edit the yaml. Run `./mvnw -q compile` first so generation happens.

## Acceptance rows you own

| Row | Assertion |
|---|---|
| W1 | valid create → 201 |
| W7 | get an existing warehouse → 200 with all fields populated |
| W8 | get an unknown business unit code → 404 |
| W9 | archive an existing warehouse → 204 |
| W10 | archive an unknown business unit code → 404 |

**Expect red.** Your endpoint tests can only pass end-to-end once the other lanes land, because the port implementations live in their worktrees. Write the tests correctly against the expected behaviour; if they can't pass in isolation, report status `red` with the reason rather than weakening assertions or stubbing out the ports. An honest red here is the expected outcome and is fine.

## Verify, then commit

```bash
cd java-assignment && ./mvnw -q compile && ./mvnw test -Dtest=WarehouseEndpointIT
```

Confirm you stayed in your lane: `git diff --name-only master`

```bash
git add -A && git commit --author="iznika <solutions@attrilio.com>" -m "feat(warehouse-api): implement warehouse endpoints and domain exception mapping"
```

No `Co-Authored-By` or attribution trailer — firm user preference. Verify with `git log -1 --pretty=format:"%(trailers)"`, which must print `[]`.

Report honestly: branch, commit sha, files changed, per-row status, literal test output, any lane violations, any contract problems for the aggregator. A truthful red beats an optimistic green.
