package com.fulfilment.application.monolith.warehouses.domain.usecases;

import com.fulfilment.application.monolith.warehouses.domain.exceptions.WarehouseNotFoundException;
import com.fulfilment.application.monolith.warehouses.domain.models.Warehouse;
import com.fulfilment.application.monolith.warehouses.domain.ports.WarehouseStore;
import java.util.ArrayList;
import java.util.List;

/**
 * Hand-written in-memory {@link WarehouseStore} modelled on the real database adapter, so the use
 * cases are exercised against the behaviour they will actually meet:
 *
 * <ul>
 *   <li>{@link #getAll()} and {@link #findByBusinessUnitCode(String)} ignore archived warehouses,
 *       while archived rows stay in the store so history can be asserted on;
 *   <li>{@link #update(Warehouse)} resolves the row by business unit code <em>among the active
 *       ones</em> and throws when there is none — it is not a blind upsert, so an operation that
 *       archives in the wrong order fails here rather than silently corrupting a generation;
 *   <li>rows are stored as copies, exactly like a database: a caller mutating the object it was
 *       handed changes nothing until it calls {@code update}.
 * </ul>
 *
 * <p>No Quarkus boot, no mocking framework — this is the whole point of having a port.
 */
class InMemoryWarehouseStore implements WarehouseStore {

  private final List<Warehouse> rows = new ArrayList<>();

  final List<String> createCalls = new ArrayList<>();
  final List<String> updateCalls = new ArrayList<>();
  final List<String> removeCalls = new ArrayList<>();

  /** Every write in the order it happened, as {@code "verb:businessUnitCode"}. */
  final List<String> callLog = new ArrayList<>();

  /** Seeds a row directly, bypassing the use cases and their validations. */
  InMemoryWarehouseStore given(Warehouse warehouse) {
    rows.add(copyOf(warehouse));
    return this;
  }

  /** Every row ever stored, archived ones included. */
  List<Warehouse> allRows() {
    return rows.stream().map(InMemoryWarehouseStore::copyOf).toList();
  }

  /** The archived rows carrying the given code, oldest first — the history of a business unit. */
  List<Warehouse> historyOf(String businessUnitCode) {
    return rows.stream()
        .filter(row -> row.archivedAt != null)
        .filter(row -> businessUnitCode.equals(row.businessUnitCode))
        .map(InMemoryWarehouseStore::copyOf)
        .toList();
  }

  @Override
  public List<Warehouse> getAll() {
    return rows.stream()
        .filter(row -> row.archivedAt == null)
        .map(InMemoryWarehouseStore::copyOf)
        .toList();
  }

  @Override
  public void create(Warehouse warehouse) {
    createCalls.add(warehouse.businessUnitCode);
    callLog.add("create:" + warehouse.businessUnitCode);
    rows.add(copyOf(warehouse));
  }

  @Override
  public void update(Warehouse warehouse) {
    updateCalls.add(warehouse.businessUnitCode);
    callLog.add("update:" + warehouse.businessUnitCode);

    var stored = activeRow(warehouse.businessUnitCode);
    if (stored == null) {
      throw new WarehouseNotFoundException(warehouse.businessUnitCode);
    }
    stored.location = warehouse.location;
    stored.capacity = warehouse.capacity;
    stored.stock = warehouse.stock;
    stored.archivedAt = warehouse.archivedAt;
  }

  @Override
  public void remove(Warehouse warehouse) {
    removeCalls.add(warehouse.businessUnitCode);
    callLog.add("remove:" + warehouse.businessUnitCode);
    rows.remove(activeRow(warehouse.businessUnitCode));
  }

  @Override
  public Warehouse findByBusinessUnitCode(String buCode) {
    var stored = activeRow(buCode);
    return stored == null ? null : copyOf(stored);
  }

  private Warehouse activeRow(String businessUnitCode) {
    return rows.stream()
        .filter(row -> row.archivedAt == null)
        .filter(row -> row.businessUnitCode.equals(businessUnitCode))
        .findFirst()
        .orElse(null);
  }

  private static Warehouse copyOf(Warehouse warehouse) {
    var copy = new Warehouse();
    copy.businessUnitCode = warehouse.businessUnitCode;
    copy.location = warehouse.location;
    copy.capacity = warehouse.capacity;
    copy.stock = warehouse.stock;
    copy.createdAt = warehouse.createdAt;
    copy.archivedAt = warehouse.archivedAt;
    return copy;
  }
}
