# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this repository is

An interview code assignment, not a production system. Two independent deliverables:

- `java-assignment/` — a Quarkus app with deliberately unimplemented methods to fill in. Tasks are in [CODE_ASSIGNMENT.md](java-assignment/CODE_ASSIGNMENT.md); three written questions live in [QUESTIONS.md](java-assignment/QUESTIONS.md) and are answered inline in that file's fenced blocks.
- `case-study/` — five discussion scenarios in [CASE_STUDY.md](case-study/CASE_STUDY.md), answered inline under each `[ fill here your answer ]` placeholder. No code.

Domain briefing (entities and the "replace" concept) is in [BRIEFING.md](case-study/BRIEFING.md). Note `java-assignment/CODE_ASSIGNMENT.md` links to `BRIEFING.md` as a sibling — the file actually lives in `case-study/`.

## Commands

All Maven commands run from `java-assignment/`. Requires JDK 17+.

```bash
./mvnw quarkus:dev
```

Dev mode with live reload; Quarkus Dev Services starts a throwaway PostgreSQL container automatically (Docker must be running). App at <http://localhost:8080/index.html>.

```bash
./mvnw package
```

Build. Also runs `quarkus:generate-code`, which regenerates the JAX-RS interfaces from the OpenAPI spec.

Unit/`@QuarkusTest` tests (Surefire, `*Test` classes):

```bash
./mvnw test
```

Single test class or method:

```bash
./mvnw test -Dtest=ProductEndpointTest
```

```bash
./mvnw test -Dtest=ProductEndpointTest#testCrudProduct
```

Run as a jar against a real PostgreSQL (the `%prod` profile in `application.properties` expects it on port 15432):

```bash
docker run -it --rm=true --name quarkus_test -e POSTGRES_USER=quarkus_test -e POSTGRES_PASSWORD=quarkus_test -e POSTGRES_DB=quarkus_test -p 15432:5432 postgres:13.3
```

```bash
java -jar ./target/quarkus-app/quarkus-run.jar
```

### Gotcha: `*IT` tests do not run by default

`maven-failsafe-plugin` is declared only inside the `native` profile in `pom.xml`, so `WarehouseEndpointIT` (a `@QuarkusIntegrationTest`) is never executed by `./mvnw verify` in a normal build. To exercise it, either add Failsafe to the default build or convert the test to `@QuarkusTest`/`*Test`. Don't assume a green `verify` means the IT passed.

## Architecture

Three subpackages under `com.fulfilment.application.monolith`, each deliberately using a *different* persistence and API style. This variety is the point — question 1 and 2 in `QUESTIONS.md` ask you to critique it. Don't "harmonize" the styles unless a task asks for it.

| Area | Persistence style | API style |
|---|---|---|
| `stores` | Active Record — `Store extends PanacheEntity`, static `Store.findById(...)` called from the resource | Hand-written JAX-RS resource |
| `products` | Repository — `ProductRepository implements PanacheRepository<Product>` | Hand-written JAX-RS resource |
| `warehouses` | Hexagonal — domain model + ports, repository adapter maps to/from a separate `DbWarehouse` entity | Generated from OpenAPI spec |

### Warehouse: ports and adapters

This is the only area with a real layering discipline, and it's where most of the assignment lives.

- `warehouses/domain/models/` — plain POJOs (`Warehouse`, `Location`). No JPA annotations; the domain must stay persistence-free.
- `warehouses/domain/ports/` — interfaces: `WarehouseStore` (outbound persistence), `LocationResolver` (outbound location lookup), and the inbound operations `CreateWarehouseOperation` / `ReplaceWarehouseOperation` / `ArchiveWarehouseOperation`.
- `warehouses/domain/usecases/` — CDI beans implementing the inbound ports; this is where the business validations belong (business unit code uniqueness, location existence, max warehouses per location, capacity vs. location max capacity, stock fits capacity, and for replacement: new capacity accommodates old stock and stocks match).
- `warehouses/adapters/database/` — `DbWarehouse` (`@Entity`, table `warehouse`) plus `WarehouseRepository`, which implements both `WarehouseStore` and `PanacheRepository<DbWarehouse>` and converts between `DbWarehouse` and the domain `Warehouse`.
- `warehouses/adapters/restapi/WarehouseResourceImpl` — implements the *generated* interface and maps the domain `Warehouse` to the generated `com.warehouse.api.beans.Warehouse` bean.
- `location/LocationGateway` — implements `LocationResolver` against a hardcoded static list of locations (each with `maxNumberOfWarehouses` and `maxCapacity`). It is not a CDI bean yet, so it won't inject into a use case as-is.

Archiving is a soft delete: `DbWarehouse.archivedAt` is set rather than the row being deleted. "Replace" means archive the existing warehouse holding a business unit code, then create a new one reusing that same code, preserving history.

### Generated OpenAPI code

`src/main/resources/openapi/warehouse-openapi.yaml` is the source of truth for the Warehouse HTTP API. `quarkus-openapi-generator-server` generates the `WarehouseResource` interface and `beans` into `target/generated-sources/...` under base package `com.warehouse.api` (configured in `application.properties`). Changing the API contract means editing the YAML and rebuilding — never hand-edit generated sources. In IntelliJ, if generated types aren't resolved, mark the generated `jaxrs` folder under `target/` as a generated sources root.

Note the spec's `/warehouse/{id}` path uses a generic `id` while the replacement path uses `businessUnitCode`; the domain identifies warehouses by business unit code, so decide and document which one `getAWarehouseUnitByID` / `archiveAWarehouseUnitByID` accept.

### Transactions and the legacy gateway

`StoreResource` methods are `@Transactional` and call `LegacyStoreManagerGateway` *inside* the transaction — so the legacy system can be notified about a change that later rolls back. Task 2 of the assignment is to make those calls fire only after commit (e.g. a CDI event observed with `@Observes(during = TransactionPhase.AFTER_SUCCESS)`, or a `Synchronization`/`TransactionSynchronizationRegistry` hook).

### Data and schema

`quarkus.hibernate-orm.database.generation=drop-and-create` with `import.sql` seeding on every boot — the schema is regenerated from the entities, so there are no migrations to write, but changing an entity changes the table. Seed data: stores/products `TONSTAD`, `KALLAX`, `BESTÅ`; warehouses `MWH.001` (ZWOLLE-001), `MWH.012` (AMSTERDAM-001), `MWH.023` (TILBURG-001). Tests assert against these exact values, and `import.sql` must stay consistent with any column added to an entity.

## Working conventions

- Unimplemented work is marked by methods throwing `UnsupportedOperationException` or a `// TODO implement this method` comment — grep for those to find the remaining scope.
- Several existing tests are stubs with their bodies commented out (`LocationGatewayTest`, `WarehouseEndpointIT#testSimpleCheckingArchivingWarehouses`, `CreateWarehouseUseCaseTest` and siblings are empty). Uncomment and flesh them out as the corresponding implementation lands.
- Both `ProductResource` and `StoreResource` declare their own nested `ErrorMapper implements ExceptionMapper<Exception>` `@Provider`. Two global mappers for the same exception type is a smell worth noting, and any new resource should not add a third.
- Code style is 2-space indent, google-java-format shape (as produced by the existing files). There is no formatter or linter plugin configured in `pom.xml` — nothing enforces this automatically.
- `pom.xml` sets `maven.compiler.release=17` while the compiler plugin still pins `source`/`target` to `11`. `release` wins, so Java 17 features compile fine (existing code already uses `var` and `Stream.toList()`); the stale `source`/`target` is leftover noise, not a real constraint. `<parameters>true</parameters>` is required for RESTEasy parameter binding — don't remove it.
