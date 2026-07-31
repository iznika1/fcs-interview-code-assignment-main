package com.fulfilment.application.monolith.warehouses.domain.usecases;

import com.fulfilment.application.monolith.warehouses.domain.models.Warehouse;
import com.fulfilment.application.monolith.warehouses.domain.ports.WarehouseStore;
import java.util.ArrayList;
import java.util.List;

/**
 * Hand-written in-memory {@link WarehouseStore}, honouring the two contracts the real repository
 * has to honour: {@link #getAll()} and {@link #findByBusinessUnitCode(String)} both ignore archived
 * warehouses, while archived rows stay in the store so history can be asserted on.
 *
 * <p>No Quarkus boot, no mocking framework — this is the whole point of having a port.
 */
class InMemoryWarehouseStore implements WarehouseStore {

  private final List<Warehouse> rows = new ArrayList<>();

  final List<String> createCalls = new ArrayList<>();
  final List<String> updateCalls = new ArrayList<>();
  final List<String> removeCalls = new ArrayList<>();

  /** Seeds a row directly, bypassing the use cases and their validations. */
  InMemoryWarehouseStore given(Warehouse warehouse) {
    rows.add(warehouse);
    return this;
  }

  /** Every row ever stored, archived ones included. */
  List<Warehouse> allRows() {
    return List.copyOf(rows);
  }

  @Override
  public List<Warehouse> getAll() {
    return rows.stream().filter(row -> row.archivedAt == null).toList();
  }

  @Override
  public void create(Warehouse warehouse) {
    createCalls.add(warehouse.businessUnitCode);
    rows.add(warehouse);
  }

  @Override
  public void update(Warehouse warehouse) {
    updateCalls.add(warehouse.businessUnitCode);
    if (!rows.contains(warehouse)) {
      rows.add(warehouse);
    }
  }

  @Override
  public void remove(Warehouse warehouse) {
    removeCalls.add(warehouse.businessUnitCode);
    rows.remove(warehouse);
  }

  @Override
  public Warehouse findByBusinessUnitCode(String buCode) {
    return rows.stream()
        .filter(row -> row.archivedAt == null)
        .filter(row -> row.businessUnitCode.equals(buCode))
        .findFirst()
        .orElse(null);
  }
}
