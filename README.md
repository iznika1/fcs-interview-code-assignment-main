# Fulfilment Monolith — code assignment

A simplified warehouse colocation management system built on **Quarkus 3.13**, **Hibernate ORM with Panache** and **PostgreSQL**.

The system manages the creation and lifecycle of warehouses and stores, and how they relate to each other — not the internal operations of either.

## What's here

| Path | Contents |
|---|---|
| [`java-assignment/`](java-assignment/) | The application. Tasks in [CODE_ASSIGNMENT.md](java-assignment/CODE_ASSIGNMENT.md), written answers in [QUESTIONS.md](java-assignment/QUESTIONS.md) |
| [`case-study/`](case-study/) | Domain [briefing](case-study/BRIEFING.md) and the five discussion scenarios in [CASE_STUDY.md](case-study/CASE_STUDY.md) |
| [CLAUDE.md](CLAUDE.md) | Architecture, commands and gotchas — the fastest way in for a new contributor |
| [SECURITY.md](SECURITY.md) | Security policy and known limitations |

## Quick start

Requires **JDK 17+** and **Docker** running. Quarkus Dev Services starts a throwaway PostgreSQL automatically — no database setup needed.

```bash
cd java-assignment
```

```bash
./mvnw quarkus:dev
```

Demo UI at <http://localhost:8080/index.html>.

> If the build fails with `class file version 55.0`, `JAVA_HOME` is pointing at a JRE 8. Maven follows `JAVA_HOME`, not the `java` on your `PATH`. See [CLAUDE.md](CLAUDE.md#gotcha-java_home-must-point-at-a-jdk-17) — this is the most common setup failure and its symptom is misleading.

## What was implemented

| Task | Summary |
|---|---|
| **1. Location** | `LocationGateway.resolveByIdentifier` resolves a location, or returns `null` when unknown |
| **2. Store** | Legacy-system notifications now fire only **after** the transaction commits, via a CDI event observed at `TransactionPhase.AFTER_SUCCESS`. A rolled-back write notifies nothing. |
| **3. Warehouse** | All endpoint handlers and use cases — create, retrieve, replace, archive — with the seven business rules enforced in the domain layer |
| **Bonus** | Fulfilment associations: warehouses assigned as fulfilment units for products per store, capped at 2 per product per store, 3 per store, and 5 product types per warehouse |

Archiving is a **soft delete**. Replacement archives the outgoing warehouse and creates its successor under the same business unit code, so the history of an area is preserved.

## Verifying

```bash
./mvnw clean verify
```

Runs 72 unit and component tests under Surefire, plus 2 integration tests under Failsafe against the packaged jar — **74 in total**, green from a cold build.

Business rules are tested with hand-written in-memory fakes: plain JUnit, no Quarkus boot, no mocking framework, 25 tests in well under a second. Persistence and endpoint behaviour use `@QuarkusTest`; the packaged artifact is covered by `WarehouseEndpointIT`.

```bash
./mvnw test -Dtest=CreateWarehouseUseCaseTest
```

Every test is independently runnable. If you run more than one Maven build at once, add `-Dquarkus.http.test-port=0` — the Quarkus test port is fixed and concurrent runs collide.

## Notes for reviewers

Three things are worth knowing before reading the code:

- **Four packages, four deliberately different styles.** `stores` uses Active Record, `products` the Repository pattern, `warehouses` ports and adapters with a generated API, `fulfilment` a repository plus service. The variety is inherited from the original codebase; [QUESTIONS.md](java-assignment/QUESTIONS.md) argues what to do about it.
- **`/warehouse/{id}` takes a business unit code**, not a row id. The OpenAPI spec names it generically, but the domain model has no id field and the sibling replacement path uses the code. That drift is discussed in answer 2.
- **The seed data violates a rule the code enforces** — `MWH.001` sits in `ZWOLLE-001` with capacity 100 against a location maximum of 40. Validation applies to new writes only; existing rows are grandfathered.

## About the code base

Based on <https://github.com/quarkusio/quarkus-quickstarts>.
