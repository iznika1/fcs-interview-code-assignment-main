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

---

## 7. The acceptance suite IS the coordination mechanism

Every test in this repo is a stub or commented out. That means parallel workers have **no objective definition of done** and merge-time review is subjective. Fix this in wave 0, before any fan-out:

> **Write the acceptance tests first, as failing tests, derived line-by-line from `CODE_ASSIGNMENT.md`.**

This solves three problems at once: it pins the contracts from §2 in executable form, it gives each worker an unambiguous target, and it turns evaluation into a countable metric ("N of 25 green") instead of an opinion.

### Acceptance matrix

Each row is one named test. Owner is the track that must turn it green. Write all of them red in wave 0.

| # | Rule (from CODE_ASSIGNMENT.md) | Owner | Fixture to use |
|---|---|---|---|
| L1 | `resolveByIdentifier` returns the matching location | A1 | `ZWOLLE-001` |
| L2 | unknown identifier → null (per §2 contract) | A1 | `NOWHERE-999` |
| S1 | successful store create → legacy gateway called once | B | new store |
| S2 | transaction rolls back → legacy gateway **not** called | B | force failure after persist |
| S3 | update and patch propagate only after commit | B | store id 1 |
| S4 | gateway receives the persisted entity, not the request body | B | store id 1 |
| W1 | valid create → 201 | A2/A3 | `AMSTERDAM-002`, cap 20, stock 5 |
| W2 | duplicate active business unit code → 400 | A2 | `MWH.001` |
| W3 | non-existent location → 400 | A2 | `ATLANTIS-001` |
| W4 | location at max warehouse count → 400 | A2 | `TILBURG-001` (max 1, has MWH.023) |
| W5 | capacity exceeds location max capacity → 400 | A2 | `TILBURG-001` (max cap 40, 30 used) |
| W6 | stock greater than capacity → 400 | A2 | cap 10 / stock 50 |
| W7 | get existing warehouse → 200 with all fields | A3 | `MWH.012` |
| W8 | get unknown → 404 | A3 | `MWH.999` |
| W9 | archive existing → 204 | A3 | `MWH.001` |
| W10 | archive unknown → 404 | A3 | `MWH.999` |
| W11 | archived row persists with `archivedAt` set (history kept) | A1 | `MWH.001` |
| W12 | list excludes archived warehouses | A1/A3 | after W9 |
| W13 | replace → old archived, new active, same BU code | A2 | `MWH.023` |
| W14 | new capacity cannot hold previous stock → 400 | A2 | `MWH.023` stock 27, new cap 10 |
| W15 | new stock must equal previous stock → 400 | A2 | `MWH.023` stock 27, new stock 5 |
| W16 | replace unknown business unit code → 404 | A2 | `MWH.999` |
| B1 | 3rd warehouse for one product+store → 400 | D | max 2 |
| B2 | 4th warehouse for one store → 400 | D | max 3 |
| B3 | 6th product type in one warehouse → 400 | D | max 5 |

### Seed-data landmine — decide this in wave 0

`import.sql` seeds `MWH.001` in `ZWOLLE-001` with **capacity 100**, but `LocationGateway` declares `ZWOLLE-001` with `maxCapacity 40`. **The seed data already violates the capacity rule.** Any worker writing W5 or W13 against `ZWOLLE-001` will get confusing results and will guess at a resolution — differently from the next worker.

Decide once, explicitly: validation applies only to new writes (existing rows are grandfathered), *or* fix the seed row. Recommendation: grandfather it, use `TILBURG-001` as the capacity fixture (30 of 40 used, clean headroom), and mention the inconsistency in QUESTIONS.md — noticing it is a point in your favour.

Related: replacing `MWH.001` is constrained too — stock must stay 10 and capacity must be ≤ 40, so it cannot reuse capacity 100.

## 8. Validation ladder — what each worker runs before declaring done

A worker may only report "done" after all five rungs pass. Rungs 1–3 are cheap and local; run them on every iteration.

| Rung | Check | Command |
|---|---|---|
| 0 | Compiles | `./mvnw -q compile` |
| 1 | No `UnsupportedOperationException` left in owned files | `grep -rn "UnsupportedOperationException" src/main/java` |
| 2 | Owned acceptance rows green | `./mvnw test -Dtest=<OwnedTestClass>` |
| 3 | Stayed inside its lane | `git diff --name-only main` matches the §3 ownership table exactly |
| 4 | Did not break neighbours | `./mvnw test` (full suite) |

Rung 3 is the one people skip and the one that causes merge pain. A worker that edited a file outside its ownership list has broken the parallelism guarantee and must flag it rather than silently merging — that edit is almost always a contract change that the other tracks need to know about.

## 9. Evaluation — is the submission actually good?

Two layers. The first is objective and automatable; the second is what an interviewer actually grades.

**Objective gate (must be 100% before considering the work complete)**

- 25 of 25 acceptance rows green (22 of 22 if you skip the bonus)
- `./mvnw test` green from a clean `target/`
- Zero `UnsupportedOperationException` and zero `// TODO implement` left in `src/main/java`
- The three previously-commented stub tests uncommented and passing
- `WarehouseEndpointIT` genuinely executes (Failsafe wired, or converted to `@QuarkusTest`) — see the wave 2 trap above

**Qualitative rubric — mirrors what a reviewer looks for**

| Dimension | Passing bar | Fails if |
|---|---|---|
| Domain purity | `domain/` imports nothing from `jakarta.ws.rs` or `jakarta.persistence` | use cases throw `WebApplicationException`, or the domain model gains JPA annotations |
| Validation placement | all 6 rules live in use cases | rules leak into `WarehouseResourceImpl` or the repository |
| HTTP semantics | validation → 400, missing → 404, archive → 204 | anything surfaces as 500 |
| Transaction correctness | legacy gateway fires only `AFTER_SUCCESS` | still called inline inside `@Transactional` |
| Test quality | use-case tests run without booting Quarkus, via fakes | every test is a slow `@QuarkusTest`, or tests mutate shared seed rows |
| Written answers | Q1/Q2 cite concrete friction from this codebase | generic textbook pros-and-cons with no reference to the actual code |

The single fastest self-check on domain purity:

```bash
grep -rn "jakarta.ws.rs\|jakarta.persistence" src/main/java/com/fulfilment/application/monolith/warehouses/domain/
```

Anything returned is a boundary violation.

## 10. Worker briefs — copy-paste prompts

Each brief is self-contained; a parallel session or subagent should need nothing but the repo and this text. Fill `<contract decisions>` from §2 before dispatching — that is the handoff payload.

**Track A1 — adapters**
> Implement `LocationGateway.resolveByIdentifier` and all four unimplemented methods of `WarehouseRepository` in the Quarkus app under `java-assignment/`. Read CLAUDE.md first. Honour these frozen contracts: `getAll()` must exclude rows where `archivedAt` is not null; `findByBusinessUnitCode` returns only the active row and returns null when absent; `LocationGateway` must be annotated `@ApplicationScoped`. Add the domain↔`DbWarehouse` mapping in both directions. Turn acceptance rows L1, L2, W11, W12 green. Touch ONLY: `location/LocationGateway.java`, `adapters/database/WarehouseRepository.java`, `adapters/database/DbWarehouse.java`, `location/LocationGatewayTest.java`, and a new `WarehouseRepositoryTest`. If you need to change any file outside that list, stop and report instead.

**Track A2 — use cases**
> Implement the business rules in the three classes under `warehouses/domain/usecases/`. Read CLAUDE.md first. Enforce: business unit code unused, location exists, location warehouse-count cap, capacity within location max capacity, stock ≤ capacity; replace additionally requires new capacity ≥ previous stock and new stock == previous stock, and must archive the old warehouse before creating the new one under the same code. Throw the domain exceptions defined in `<contract decisions>` — never `jakarta.ws.rs` types; the domain package must not import JAX-RS or JPA. Test with hand-written in-memory fakes for `WarehouseStore` and `LocationResolver` — plain JUnit, no Quarkus boot, no new Maven dependencies. Note that seed row `MWH.001` already violates the capacity rule; validation applies to new writes only. Turn acceptance rows W2–W6, W13–W16 green. Touch ONLY the three use-case classes and their three test files.

**Track A3 — REST adapter**
> Implement the four methods of `WarehouseResourceImpl` against the inbound ports, plus an `ExceptionMapper` in the adapter layer translating domain exceptions to the statuses in `warehouse-openapi.yaml` (validation → 400, not found → 404, archive → 204). Read CLAUDE.md first. `{id}` in the path means `<contract decisions #4>`. Do not hand-edit anything under `target/` — the `WarehouseResource` interface is generated from the OpenAPI spec. Turn acceptance rows W1, W7–W10 green and uncomment the assertions in `WarehouseEndpointIT`. Touch ONLY `adapters/restapi/`, and `WarehouseEndpointIT.java`.

**Track B — store after-commit**
> In `stores/StoreResource`, the legacy gateway is called inside `@Transactional`, so the legacy system can be told about a change that later rolls back. Make those calls fire only after the transaction commits, using a CDI event observed with `@Observes(during = TransactionPhase.AFTER_SUCCESS)`. Also fix two adjacent bugs: `update`/`patch` pass the detached request body to the gateway instead of the persisted entity, and `patch`'s null-guards are inverted (it checks `entity.name != null` before overwriting from `updatedStore`, so it is not a real partial update). Turn acceptance rows S1–S4 green; make the gateway observable via a test double rather than asserting on temp files. Touch ONLY the `stores/` package and its tests.

**Track C — written answers**
> Answer the five scenarios in `case-study/CASE_STUDY.md` (pure business discussion, no code needed) and the three questions in `java-assignment/QUESTIONS.md`. For Q1 and Q2, cite concrete friction in THIS codebase, not generic pros and cons: three coexisting persistence styles (Active Record in `stores`, Repository in `products`, ports-and-adapters in `warehouses`); the OpenAPI spec's `{id}` versus the domain's business-unit-code identity; the duplicated `ErrorMapper` in two resources; seed data that violates the location capacity rule. Touch ONLY those two markdown files.

---

## 11. Partition map — session and worker names

One identifier per worker, reused verbatim as the session title, branch name, and commit scope. If those three ever disagree, the partition has leaked.

### Identifier grammar

```
w<wave>-<track><index>-<slug>          e.g.  w1-a2-usecases
```

Wave 0 and the convergence waves are solo, so they carry no track letter: `w0-contracts`, `w2-integrate`, `w4-polish`.

### Master partition table

| Worker ID | Wave | Branch | Commit scope | Acceptance rows | Parallel with |
|---|---|---|---|---|---|
| `w0-contracts` | 0 | `main` | `chore(contracts)` | authors all 25 red | — (solo, blocks everything) |
| `w1-a1-adapters` | 1 | `track/w1-a1-adapters` | `feat(warehouse-db)` | L1, L2, W11, W12 | a2, a3, b1, c1, c2 |
| `w1-a2-usecases` | 1 | `track/w1-a2-usecases` | `feat(warehouse-domain)` | W2–W6, W13–W16 | a1, a3, b1, c1, c2 |
| `w1-a3-restapi` | 1 | `track/w1-a3-restapi` | `feat(warehouse-api)` | W1, W7–W10 | a1, a2, b1, c1, c2 |
| `w1-b1-store-txn` | 1 | `track/w1-b1-store-txn` | `fix(stores)` | S1–S4 | all of wave 1 |
| `w1-c1-questions` | 1 | `track/w1-c1-questions` | `docs(questions)` | none | all of wave 1 |
| `w1-c2-casestudy` | 1 | `track/w1-c2-casestudy` | `docs(case-study)` | none | all of wave 1 |
| `w2-integrate` | 2 | `main` | `chore(integration)` | verifies all 22 | — (solo) |
| `w3-d1-fulfilment` | 3 | `track/w3-d1-fulfilment` | `feat(fulfilment)` | B1–B3 | — (solo) |
| `w4-polish` | 4 | `main` | `docs(polish)` | verifies all 25 | — (solo) |

Commit scopes follow conventionalcommits.org, which `CONTRIBUTING.md` asks for. Scope names are deliberately *not* the worker IDs — they describe the code area, so the history stays readable after the plan is deleted.

### File lanes — the actual partition

Paths are relative to `java-assignment/src/` unless noted. A worker touching anything outside its lane has broken the guarantee and must escalate, not merge.

| Worker ID | Owns exclusively |
|---|---|
| `w1-a1-adapters` | `main/java/**/location/LocationGateway.java`, `main/java/**/warehouses/adapters/database/**`, `test/java/**/location/**`, `test/java/**/warehouses/adapters/database/**` |
| `w1-a2-usecases` | `main/java/**/warehouses/domain/usecases/**`, `test/java/**/warehouses/domain/usecases/**` |
| `w1-a3-restapi` | `main/java/**/warehouses/adapters/restapi/**`, `test/java/**/warehouses/adapters/restapi/**` |
| `w1-b1-store-txn` | `main/java/**/stores/**`, `test/java/**/stores/**` |
| `w1-c1-questions` | `java-assignment/QUESTIONS.md` |
| `w1-c2-casestudy` | `case-study/CASE_STUDY.md` |
| `w3-d1-fulfilment` | new `**/fulfilment/**` package + tests, and `main/resources/import.sql` (unfrozen in wave 3 because it runs solo) |

### Frozen zone — `w0-contracts` owns these, nobody edits them during wave 1

- `pom.xml`, `main/resources/application.properties`, `main/resources/import.sql`
- `main/resources/openapi/warehouse-openapi.yaml`
- `main/java/**/warehouses/domain/ports/**` and `**/domain/models/**`
- the new domain-exception classes
- `CLAUDE.md`, `WORKPLAN.md`
- anything under `target/` (generated — never hand-edited by anyone)

The ports and models are frozen because they are the shared contract; a wave-1 worker that needs a port change has found a §2 decision that was made wrong, and that is an escalation, not a local edit.

### Test-file ownership handoff

`w0-contracts` **authors** every acceptance test as failing, then hands each file to its wave-1 owner. From wave 1 onward the owner is the only writer.

| Test class | Authored by | Owned from wave 1 by |
|---|---|---|
| `LocationGatewayTest` | `w0-contracts` | `w1-a1-adapters` |
| `WarehouseRepositoryTest` (new) | `w0-contracts` | `w1-a1-adapters` |
| `CreateWarehouseUseCaseTest` | `w0-contracts` | `w1-a2-usecases` |
| `ReplaceWarehouseUseCaseTest` | `w0-contracts` | `w1-a2-usecases` |
| `ArchiveWarehouseUseCaseTest` | `w0-contracts` | `w1-a2-usecases` |
| `WarehouseEndpointIT` | exists (stub) | `w1-a3-restapi` |
| `StoreLegacySyncTest` (new) | `w0-contracts` | `w1-b1-store-txn` |
| `FulfilmentResourceTest` (new) | `w3-d1-fulfilment` | `w3-d1-fulfilment` |

`ProductEndpointTest` is owned by nobody and must not be modified — it is the untouched-baseline canary. If it goes red, something global broke.

### Dispatch commands

Create the lane before starting a worker:

```bash
git checkout main && git checkout -b track/w1-a2-usecases
```

Rung 3 of the validation ladder, per worker — prints anything that escaped the lane:

```bash
git diff --name-only main
```

Converge in this order at wave 2, least to most entangled, so conflicts surface early and cheap:

```bash
git checkout main && git merge --no-ff track/w1-c2-casestudy track/w1-c1-questions track/w1-b1-store-txn track/w1-a1-adapters track/w1-a2-usecases track/w1-a3-restapi
```
