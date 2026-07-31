# Session brief: w1-b1-store-txn

Open a Claude Code session with cwd `../w1-b1-store-txn` and paste everything below the line.

---

You are worker `w1-b1-store-txn`, one of six running in parallel on isolated git worktrees. You are on branch `fix/stores-after-commit-sync`. Other workers cannot see your changes and you cannot see theirs — that is intentional.

Read these three files at the repo root before touching anything: `CLAUDE.md` (architecture and commands), `CONTRACTS.md` (C1–C9, frozen and non-negotiable), `WORKPLAN.md` sections 7–11 (acceptance matrix, validation ladder, partition map).

The Maven project is in `java-assignment/`. Run all mvnw commands from there.

## Your lane — touch nothing else

```
java-assignment/src/main/java/com/fulfilment/application/monolith/stores/**
java-assignment/src/test/java/com/fulfilment/application/monolith/stores/**
```

Frozen zone, owned by nobody: `pom.xml`, `application.properties`, `import.sql`, `CLAUDE.md`, `CONTRACTS.md`, `WORKPLAN.md`, anything under `target/`. No new Maven dependencies (C9). The `products/` and `warehouses/` packages are outside your lane.

## The bug

`StoreResource.create`/`update`/`patch` are `@Transactional` and call `LegacyStoreManagerGateway` **inside** the transaction. If the transaction later rolls back, the downstream legacy system has been told about a change that was never persisted.

## Your job

1. Make the legacy calls fire only **after** the transaction commits, using a CDI event observed with `@Observes(during = TransactionPhase.AFTER_SUCCESS)`. Create a small event type in the `stores` package, fire it from the resource with `jakarta.enterprise.event.Event`, and observe it in a component that calls the gateway. These APIs come with the Quarkus BOM — no new dependencies.

2. Fix two adjacent bugs in the same methods:
   - `update()` and `patch()` pass `updatedStore` (the detached request body, which has no id) to the gateway instead of the persisted entity. Pass the persisted entity.
   - `patch()` has inverted null-guards: it checks `entity.name != null` before overwriting from `updatedStore`, so it isn't a real partial update. It should apply a field from `updatedStore` only when that incoming field is present.

## Acceptance rows you own

| Row | Assertion |
|---|---|
| S1 | a successful store create notifies the legacy gateway exactly once |
| S2 | a transaction that rolls back does **not** notify the legacy gateway |
| S3 | update and patch propagate only after commit |
| S4 | the gateway receives the persisted entity, not the raw request body |

**Testing note:** `LegacyStoreManagerGateway` currently writes and deletes temp files, which is useless to assert on. Make it observable — an injectable `@Alternative` test double, or a counter the test can read. Adjusting `LegacyStoreManagerGateway` is inside your lane. Prefer a design where production behaviour is unchanged but the collaborator can be substituted or counted in tests.

Use `@QuarkusTest`. Beware that `import.sql` seeds shared rows and `ProductEndpointTest` deletes product 1, so prefer `@TestTransaction` or your own rows over mutating seeded stores.

## Verify, then commit

```bash
cd java-assignment && ./mvnw -q compile && ./mvnw test -Dtest=StoreLegacySyncTest
```

Confirm you stayed in your lane: `git diff --name-only master`

```bash
git add -A && git commit --author="iznika <solutions@attrilio.com>" -m "fix(stores): propagate legacy sync only after transaction commit"
```

No `Co-Authored-By` or attribution trailer — firm user preference. Verify with `git log -1 --pretty=format:"%(trailers)"`, which must print `[]`.

Report honestly: branch, commit sha, files changed, per-row status, literal test output, any lane violations, any contract problems for the aggregator. A truthful red beats an optimistic green.
