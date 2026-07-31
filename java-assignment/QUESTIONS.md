# Questions

Here we have 3 questions related to the code base for you to answer. It is not about right or wrong, but more about what's the reasoning behind your decisions.

1. In this code base, we have some different implementation strategies when it comes to database access layer and manipulation. If you would maintain this code base, would you refactor any of those? Why?

**Answer:**
```txt
Three styles coexist. `stores` is Active Record: `Store extends PanacheEntity` and
`StoreResource` calls `Store.findById(id)` statically. `products` is Repository:
`ProductRepository implements PanacheRepository<Product>`, injected. `warehouses` is
ports-and-adapters: a persistence-free `Warehouse` POJO, a `WarehouseStore` port, and
`WarehouseRepository` mapping to a separate `DbWarehouse` entity.

I would not converge all three. I would move `stores`, leave `products`, and keep
`warehouses` as the reference.

The concrete argument is testability, not purity. `CreateWarehouseUseCase` takes
`WarehouseStore` through its constructor, so its rules can be exercised with
`new CreateWarehouseUseCase(fakeStore)` — plain JUnit, milliseconds, no Docker. Nothing
equivalent is possible for `StoreResource`: `Store.findById` is a static call into an
active persistence context, so every assertion about store behaviour needs a booted
Quarkus and a Dev Services PostgreSQL. That matters here because `StoreResource` is
where the real defect lives — `legacyStoreManagerGateway.createStoreOnLegacySystem` runs
inside `@Transactional`, so the legacy system is told about a store that may still roll
back. Fixing that safely wants a seam, and Active Record denies one.

I would leave `products` alone. The repository already provides the injection point;
the remaining flaw is different — `ProductResource.get()` returns `List<Product>`, so
the JPA entity *is* the wire contract and any column added to it silently changes the
API. That is worth fixing before the persistence style is.

Cost versus benefit is the deciding factor. Full hexagonal layering costs a port, an
adapter, a DB entity and a mapper per aggregate. `Warehouse` carries roughly seven
invariants (unique active business unit code, location exists, warehouses-per-location
cap, capacity within the location maximum, stock within capacity, plus the two
replacement rules) and repays that cost. `Product` validates that a name is not null.
Layering should scale with rule density, not be applied uniformly.
```
----
2. When it comes to API spec and endpoints handlers, we have an Open API yaml file for the `Warehouse` API from which we generate code, but for the other endpoints - `Product` and `Store` - we just coded directly everything. What would be your thoughts about what are the pros and cons of each approach and what would be your choice?

**Answer:**
```txt
Spec-first wins here, but this codebase shows exactly where it stops helping.

The real benefit is that `warehouse-openapi.yaml` is regenerated on every build, so
`WarehouseResourceImpl` cannot silently drift from the published contract — a signature
change becomes a compile error rather than a surprise for a consumer. It also forces a
boundary: the generated `com.warehouse.api.beans.Warehouse` is a distinct type from the
domain `Warehouse`, so the adapter must map, and the domain model cannot leak onto the
wire. Compare `ProductResource.get()`, which returns `List<Product>` — the JPA entity is
the contract, and there is no published document to review a change against at all.

The cost is that the generator faithfully propagates whatever the spec gets wrong, and
cannot arbitrate. `/warehouse/{id}` names its parameter `id` with example `"456"`, while
the sibling `/warehouse/{businessUnitCode}/replacement` uses the other name — and the
domain `Warehouse` model has no id field whatsoever. Something had to decide what `{id}`
means; the tool could not. We resolved it as the business unit code, on the grounds that
the briefing calls that code the warehouse's identity and the replacement path already
keys on it. That is a design decision the spec obscured rather than captured.

Generated code also stops short of the error contract. The spec declares 400 and 404
with no response schema, so translating `WarehouseValidationException` and
`WarehouseNotFoundException` into those statuses is hand-written in the adapter either
way. Meanwhile the hand-coded side has drifted precisely where no spec was watching:
`ProductResource` and `StoreResource` each declare their own nested
`ErrorMapper implements ExceptionMapper<Exception>` — two global providers for the same
exception type, identical by copy-paste.

My choice: spec-first for anything crossing a team boundary, generating the interface
only. But treat the YAML as a reviewed artefact, not a rubber stamp — the `{id}`
ambiguity survived because nobody read it against the domain.
```
----
3. Given the need to balance thorough testing with time and resource constraints, how would you prioritize and implement tests for this project? Which types of tests would you focus on, and how would you ensure test coverage remains effective over time?

**Answer:**
```txt
The starting point is close to zero: `CreateWarehouseUseCaseTest` is literally an empty
class body, its two siblings likewise, `LocationGatewayTest` is commented out, and
`WarehouseEndpointIT#testSimpleCheckingArchivingWarehouses` is a block of commented-out
assertions.

Base of the pyramid, and where I would spend the first hours: use-case tests with
hand-written in-memory fakes for `WarehouseStore` and `LocationResolver`. The ports make
this free — `CreateWarehouseUseCase` already takes its store through the constructor —
and every business rule lives at this level. These run in milliseconds with no Quarkus
boot and no Dev Services container, so they can run on every save.

Within that, the replacement flow comes first. Create and archive touch one row;
replacement archives one warehouse and creates another under the same business unit
code, so a wrong implementation silently destroys history — the one thing the feature
exists to preserve.

Middle layer: a small number of `@QuarkusTest` slices against the repository. Fakes
cannot prove that `getAll()` and `findByBusinessUnitCode` actually filter on
`archivedAt IS NULL`; that is a query-level claim and needs a real database.

Top: a handful of endpoint tests for wiring, status codes and serialization only. Each
one costs a container start, so they verify plumbing, not rules.

Keeping it honest over time comes down to three fixes. First, `maven-failsafe-plugin` is
declared only inside the `native` profile, so `WarehouseEndpointIT` never runs in a
normal build and a green `mvnw verify` is misleading — either move Failsafe into the
default build or make it a `@QuarkusTest`. Second, remove the shared mutable fixture:
`ProductEndpointTest` deletes product 1 from the `import.sql` seed, so any second test
listing products becomes order-dependent; tests should create their own data or use
`@TestTransaction`. Third, do not trust the seed as a valid fixture — it puts `MWH.001`
at capacity 100 into `ZWOLLE-001`, whose maximum is 40, so it already violates the rule
under test. I would track asserted rules, not line coverage; coverage over `Product`
CRUD is noise.
```
