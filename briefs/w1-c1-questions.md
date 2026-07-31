# Session brief: w1-c1-questions

Open a Claude Code session with cwd `../w1-c1-questions` and paste everything below the line.

---

You are worker `w1-c1-questions`, one of six running in parallel on isolated git worktrees. You are on branch `track/w1-c1-questions`.

## Your lane — touch nothing else, write no code

```
java-assignment/QUESTIONS.md
```

Answer the three questions inside the existing fenced blocks. Keep the file structure intact — write inside the ```` ```txt ```` fences that are already there.

Read `CLAUDE.md` and the actual source before writing. These are graded on reasoning, not length. Aim for roughly 200–350 words per answer, and ground **every** claim in something concrete from this codebase. Generic textbook pros-and-cons is the failure mode.

## Q1 — database access strategies

Three styles coexist here, all citable:

- **`stores/`** — Active Record. `Store extends PanacheEntity`, static `Store.findById` called straight from the resource, so persistence and HTTP handling sit in one class and the resource can't be unit tested without a database.
- **`products/`** — Repository. `ProductRepository implements PanacheRepository<Product>`, injected into the resource. Testable, but `Product` is still exposed directly as the API payload.
- **`warehouses/`** — ports and adapters. A persistence-free domain model, a `WarehouseStore` port, and a `WarehouseRepository` adapter mapping to a separate `DbWarehouse` entity.

Take a position on what you'd converge on and what you'd leave alone, and be explicit about migration cost versus benefit. The strongest concrete argument: the warehouse style is the only one where business rules can be unit tested without booting Quarkus.

## Q2 — OpenAPI-generated versus hand-coded endpoints

Cite the specific friction:

- The spec is the source of truth for the Warehouse API and the interface is regenerated every build, so the implementation can't silently drift from the published contract.
- But `/warehouse/{id}` names its parameter `id` with example `"456"`, while the domain identifies warehouses by business unit code and the domain model has **no id field at all**. The sibling path `/warehouse/{businessUnitCode}/replacement` uses the other name. This project resolved it as the business unit code (`CONTRACTS.md` C4) — a decision the generator could not make for you.
- Generated code also can't express the domain error contract: the spec declares 400 and 404, but mapping domain exceptions onto them is still hand-written in the adapter.
- The hand-coded `Product` and `Store` resources have no published contract at all, and each carries its own duplicated nested `ErrorMapper` for the same exception type — a divergence a spec-first approach would have made visible.

State which you'd choose for this codebase and why.

## Q3 — test prioritisation under time pressure

Reference this repo's actual state: nearly every test is a stub or commented out; use-case rules can be tested with plain JUnit fakes in milliseconds while endpoint tests each boot Quarkus and a Dev Services PostgreSQL; `import.sql` seeds shared state that `ProductEndpointTest` mutates by deleting product 1, making endpoint tests order-sensitive; and `maven-failsafe-plugin` is declared only in the `native` profile, so `WarehouseEndpointIT` never runs in a normal build and a green `mvnw verify` is misleading.

Describe the pyramid you'd build, what you'd test first given limited time, and how you'd keep coverage honest over time.

## Commit

```bash
git add -A && git commit --author="iznika <solutions@attrilio.com>" -m "docs(questions): answer code-base reasoning questions"
```

No `Co-Authored-By` or attribution trailer — firm user preference. Verify with `git log -1 --pretty=format:"%(trailers)"`, which must print `[]`. Confirm you stayed in your lane with `git diff --name-only master`.
