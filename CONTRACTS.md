# Frozen Contracts (w0-contracts)

Shared truth for all wave-1 workers. Resolved before fan-out. **Do not renegotiate locally** — if a contract here is wrong, stop and escalate to the aggregator session.

Scratch coordination doc. **Delete before submitting.**

## C1 — `WarehouseStore.getAll()` excludes archived

Returns only rows where `archivedAt IS NULL`. Today it is `listAll()`, which returns archived rows too.

Evidence: the commented-out `WarehouseEndpointIT#testSimpleCheckingArchivingWarehouses` archives a warehouse and then asserts it is **absent** from the list.

## C2 — `findByBusinessUnitCode` returns the ACTIVE row, or `null`

Archived rows deliberately share a business unit code (that is what "replace" means), so an unfiltered lookup returns multiple rows. Filter on `archivedAt IS NULL`.

Returns `null` when there is no active match — it does **not** throw. This matches the existing codebase style (`Store.findById` returns null, the resource decides the status code). Translating `null` into 404 is the REST adapter's job.

## C3 — Location queries filter `getAll()` in the use case; ports do NOT change

The "max warehouses per location" and "max capacity per location" rules need warehouses-by-location, which `WarehouseStore` does not expose. **Do not add a port method.** Filter the result of `getAll()` inside the use case.

Because of C1, `getAll()` is already active-only, which is exactly the right basis for both the count cap and the summed-capacity cap.

## C4 — `{id}` in the REST path means `businessUnitCode`

`/warehouse/{id}` GET and DELETE both key on the **business unit code**, not the database primary key.

Rationale: the domain `Warehouse` model has no `id` field at all, the briefing calls the business unit code "the Business Unit Code that identifies every Warehouse", and the sibling `/warehouse/{businessUnitCode}/replacement` path already uses it. The spec's `id` naming and its `"456"` example are misleading.

Consequence: the commented-out IT calls `delete("warehouse/1")` and must become `delete("warehouse/MWH.001")`. `w1-a3-restapi` owns that edit.

Document this choice in QUESTIONS.md answer 2 — it is a concrete example of generated-spec versus domain drift.

## C5 — Domain exceptions, already created

Two exception types now exist in `warehouses/domain/exceptions/`:

| Exception | HTTP status | Use for |
|---|---|---|
| `WarehouseValidationException` | 400 | every business-rule violation |
| `WarehouseNotFoundException` | 404 | unknown business unit code |

`w1-a2-usecases` throws them. `w1-a3-restapi` writes the `ExceptionMapper` that translates them, in the adapter layer.

**The domain package must never import `jakarta.ws.rs` or `jakarta.persistence`.** Throwing `WebApplicationException` from a use case defeats the hexagonal structure the assignment is testing. Self-check:

```bash
grep -rn "jakarta.ws.rs\|jakarta.persistence" src/main/java/com/fulfilment/application/monolith/warehouses/domain/
```

Any hit is a boundary violation.

## C6 — `LocationGateway` becomes `@ApplicationScoped`

It has no bean-defining annotation today, so `@Inject LocationResolver` will not resolve. `w1-a1-adapters` applies the annotation.

## C7 — `remove()` is a hard delete and stays unused by the archive flow

Archiving is a **soft** delete: set `archivedAt` and call `update()`. Implement `remove()` as a real Panache delete for interface completeness, but `ArchiveWarehouseUseCase` must not call it — history preservation is the whole point of the replace feature.

## C8 — Seed data violates the capacity rule; grandfather it

`import.sql` seeds `MWH.001` into `ZWOLLE-001` with **capacity 100**, but `LocationGateway` declares `ZWOLLE-001` with `maxCapacity 40`. The seed data already breaks the rule you are enforcing.

Resolution: **validation applies to new writes only.** Existing rows are grandfathered; do not "fix" `import.sql`, and do not add a startup validation.

For the capacity rule, use `AMSTERDAM-002` (empty, 3 slots, `maxCapacity 75` — request 80) or `ZWOLLE-002` (2 slots, `maxCapacity 50`, 30 used — request 25).

**Do not use `TILBURG-001` for the capacity rule.** It has `maxNumberOfWarehouses 1` and `MWH.023` already occupies it, so on a create the *count* cap fires before the capacity cap ever runs and the error says "maximum warehouse(s)", not a capacity message. `TILBURG-001` is the correct fixture for the **count** rule (W4), not the capacity rule (W5).

Related trap: replacing `MWH.001` is constrained by this too. The replacement must keep stock at 10 and cannot exceed capacity 40, so it cannot reuse capacity 100.

`w1-c1-questions` should mention this inconsistency — noticing it reads well.

## C9 — No new Maven dependencies

`pom.xml` is frozen. Test use cases with hand-written in-memory fakes for `WarehouseStore` and `LocationResolver`: plain JUnit, no Quarkus boot, no Mockito. Fast, dependency-free, and it demonstrates the point of the ports.

`@QuarkusTest` is still correct for the REST and persistence layers.

## Reference — location table

From `LocationGateway`, as `(maxNumberOfWarehouses, maxCapacity)`:

| Location | Max warehouses | Max capacity | Seeded |
|---|---|---|---|
| ZWOLLE-001 | 1 | 40 | MWH.001 — cap 100, stock 10 **(violates C8)** |
| ZWOLLE-002 | 2 | 50 | — |
| AMSTERDAM-001 | 5 | 100 | MWH.012 — cap 50, stock 5 |
| AMSTERDAM-002 | 3 | 75 | — |
| TILBURG-001 | 1 | 40 | MWH.023 — cap 30, stock 27 |
| HELMOND-001 | 1 | 45 | — |
| EINDHOVEN-001 | 2 | 70 | — |
| VETSBY-001 | 1 | 90 | — |

`AMSTERDAM-002` (empty, 3 slots, 75 capacity) is the clean fixture for happy-path creation.
