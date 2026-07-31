# Work Plan

Scratch coordination doc for splitting the assignment across sessions. **Delete before submitting.**

## 1. Real dependency graph

```
Session 0: lock contracts  ───┬──> Track A (warehouse)  A1 adapters ──> A2 use cases ──> A3 REST
                              │
                              ├──> Track B (store transactional fix)   [independent]
                              │
                              └──> Track C (QUESTIONS + CASE_STUDY)    [independent]

                    converge + verify ──> Track D (bonus fulfilment)  ──> polish QUESTIONS
```

What actually blocks what:

| Task | Blocks | Blocked by |
|---|---|---|
| 1. Location `resolveByIdentifier` | Warehouse validations | contracts |
| 2. Store after-commit | nothing | nothing |
| 3a. `WarehouseRepository` (port impl) | 3b, 3c end-to-end | contracts |
| 3b. 3 use cases | 3c | contracts (testable with fakes before 3a exists) |
| 3c. `WarehouseResourceImpl` | bonus | inbound ports (already defined) |
| BONUS. fulfilment units | nothing | 3 (needs warehouse lookup) |
| QUESTIONS.md | nothing | Q1/Q2 read better after touching code |
| CASE_STUDY.md | nothing | nothing — pure business writing |

## 2. Session 0 — lock these contracts first (~20 min, solo, do NOT skip)

Every one of these is a decision that two tracks would otherwise make differently. Fanning out before these are pinned is where parallel work goes wrong.

1. **`WarehouseStore.getAll()` must exclude archived** (`archivedAt IS NULL`). Evidence: the commented-out `WarehouseEndpointIT#testSimpleCheckingArchivingWarehouses` archives a warehouse then asserts `ZWOLLE-001` is *absent* from the list. Today `getAll()` is `listAll()` — returns archived rows too.
2. **`findByBusinessUnitCode` returns the ACTIVE row only.** Archived rows deliberately share a BU code (that is what "replace" means), so an unfiltered lookup returns 2+ rows. Also decide: `null` vs exception on miss.
3. **How the location checks query data.** `WarehouseStore` has no query-by-location, but "max warehouses per location" and "max capacity per location" both need it. Either filter `getAll()` inside the use case (no port change — simplest) or add `findByLocation(String)`. Pick now; A1 and A2 both depend on it.
4. **What `{id}` means in `/warehouse/{id}`.** The spec says `id` (example `"456"`), the replacement path says `businessUnitCode`, and the domain `Warehouse` model has **no id field at all**. The commented-out IT calls `delete("warehouse/1")` — i.e. the DB primary key. Recommendation: treat `{id}` as the **business unit code** (domain-consistent, matches "the Business Unit Code that identifies every Warehouse" in the briefing), and update that commented test to `delete("warehouse/MWH.001")`. Document the choice in QUESTIONS.md answer 2 — it is a good concrete example of spec-vs-domain drift.
5. **Validation failure → HTTP status.** Spec declares 400 and 404 only; unmapped exceptions become 500. Define domain exceptions now (e.g. `WarehouseValidationException`, `WarehouseNotFoundException`) so A2 can throw them and A3 can map them. Do **not** throw `jakarta.ws.rs.WebApplicationException` from the use cases — leaking JAX-RS into the domain defeats the hexagonal structure the assignment is testing.
6. **`LocationGateway` needs `@ApplicationScoped`.** It has no bean-defining annotation today, so `@Inject LocationResolver` will not resolve.
7. **`remove()` on the port.** Archive is a soft delete via `update()`, so decide whether `remove()` is a hard delete or stays unused.

Also do in session 0, because they are shared-file edits that cause merge pain later:
- `git init && git add -A && git commit` — see §5.
- Any `pom.xml` test-dependency additions. (Recommendation: none. Hand-rolled in-memory fakes for `WarehouseStore`/`LocationResolver` need zero deps, run in milliseconds without booting Quarkus, and demonstrate the point of the ports. Add Mockito/AssertJ only if you want it — but add it *now*, not mid-fan-out.)

## 3. Track definitions (file ownership is disjoint by design)

### Track A — Warehouse (the bulk; ~55% of effort)
Run A1 → A2 → A3 sequentially in one session, or split if you want more parallelism (see §4).

| Step | Owns | Done when |
|---|---|---|
| A1 adapters | `location/LocationGateway.java`, `adapters/database/WarehouseRepository.java`, `DbWarehouse.java`, `LocationGatewayTest.java` | all 5 `UnsupportedOperationException`s gone; `getAll()` filters archived; domain↔`DbWarehouse` mapping both ways |
| A2 use cases | `domain/usecases/*.java` (3), their 3 test files | all 6 validations enforced with unit tests against fakes: BU code unique, location exists, location warehouse-count cap, capacity ≤ location max capacity, stock ≤ capacity; replace adds: new capacity ≥ old stock, new stock == old stock; replace archives old + creates new under the same BU code |
| A3 REST | `adapters/restapi/WarehouseResourceImpl.java`, new exception mapper, `WarehouseEndpointIT.java` | 4 endpoints implemented; 400/404 mapped correctly; commented-out IT assertions uncommented and green |

### Track B — Store after-commit (independent, ~30 min)
Owns: `stores/StoreResource.java`, `stores/LegacyStoreManagerGateway.java`, new event class, new `StoreResourceTest`.

The bug: `create`/`update`/`patch` are `@Transactional` and call the legacy gateway *inside* the transaction, so a later rollback leaves the legacy system holding data that never persisted. Fix with a CDI event observed at `@Observes(during = TransactionPhase.AFTER_SUCCESS)` — idiomatic Quarkus and easy to unit-test. (`TransactionSynchronizationRegistry` works too but is clunkier.)

Two extra bugs live in the same methods — fix them while you are there and mention them:
- `update`/`patch` pass `updatedStore` (the detached request body, no id) to the gateway instead of the persisted `entity`.
- `patch` has inverted null-guards: it checks `entity.name != null` before overwriting from `updatedStore`, so it is not a real partial update.

### Track C — Writing (independent, can start immediately)
Owns: `java-assignment/QUESTIONS.md`, `case-study/CASE_STUDY.md`.

`CASE_STUDY.md` (5 scenarios) needs zero code context — start it first, or hand it to a subagent. `QUESTIONS.md` Q1 (persistence strategies) and Q2 (OpenAPI-first vs hand-coded) are much stronger written *after* A and B land, because you will have concrete friction to cite — e.g. the `{id}` ambiguity from Session 0 #4 is a perfect Q2 example, and the three coexisting persistence styles are Q1. Draft skeletons now, finalise in the polish pass.

### Track D — Bonus (after A converges)
New package (e.g. `fulfilment/`) with an association entity carrying store + product + warehouse, enforcing: ≤2 warehouses per product per store, ≤3 warehouses per store, ≤5 product types per warehouse. Touches `import.sql` (shared file) — that is why it runs after the others land.

## 4. How much parallelism is actually worth it

The assignment budgets ~4h. Coordination overhead is real, so:

- **Recommended: 3 concurrent tracks** — A, B, C — after Session 0. This is the sweet spot.
- **A1/A2/A3 as three parallel sessions is possible** (their file sets genuinely do not overlap, and A2 can be built test-first against fakes before A1 exists) **but only because Session 0 froze the port contracts.** Worth it if you want max speed; otherwise the handoff cost roughly equals the implementation cost at this size.
- **Do not parallelize** Track D, or the final verification pass.
- **Subagents vs sessions:** use subagents for Track C (self-contained writing, returns a finished artifact) and for read-only investigation. Use real sessions for A and B, which need iterative `mvnw test` cycles.

### Machine contention — the practical limit
Each `@QuarkusTest` run starts a throwaway PostgreSQL via Dev Services and boots the app (~10–20s). Three sessions running `mvnw test` simultaneously means three containers plus three JVMs. Ports are randomised so it works, but it thrashes. Mitigations:
- Keep **one** `./mvnw quarkus:dev` running for manual pokes; do not start it per session.
- Track A2's use-case tests need no Quarkus boot at all if you use plain-JUnit fakes — that keeps the fast inner loop off Docker entirely.
- Stagger the full `mvnw test` runs.

### Seeded-state hazard when adding tests
`drop-and-create` + `import.sql` means every `@QuarkusTest` shares seeded rows, and `ProductEndpointTest` *deletes* product 1. New tests that mutate seed data are order-dependent. Use `@TestTransaction` or create your own rows rather than mutating `MWH.001`/`TONSTAD`.

## 5. Prerequisite: initialise git

There is no `.git` here, which means no worktrees, no branches per track, and no undo if a parallel session goes wrong. Before any fan-out:

```bash
git init && git add -A && git commit -m "chore: baseline import of assignment skeleton"
```

Then one branch per track and merge at the converge point. A baseline commit also makes the final diff reviewable, which is worth something in an interview submission.

## 6. Suggested order

| Wave | What | Parallel? |
|---|---|---|
| 0 | git init, lock the 7 contracts, pom decision | solo, ~20 min |
| 1 | Track A (A1→A2→A3) ‖ Track B ‖ Track C draft | 3 tracks |
| 2 | Merge, full `mvnw test`, uncomment the stub tests, wire Failsafe or convert `WarehouseEndpointIT` so it actually runs | solo |
| 3 | Track D bonus | solo |
| 4 | Finalise QUESTIONS.md with real friction encountered; delete this file | solo |

Wave 2 has a trap worth repeating: `maven-failsafe-plugin` is declared **only in the `native` profile**, so `WarehouseEndpointIT` never executes in a normal build. A green `mvnw verify` does not mean the IT passed. Either add Failsafe to the default build or convert it to `@QuarkusTest`.
