package com.fulfilment.application.monolith.warehouses.domain.usecases;

import com.fulfilment.application.monolith.warehouses.domain.models.Warehouse;
import java.time.LocalDateTime;

/** Small builder so the tests read as data, not as field assignments. */
final class WarehouseFixtures {

  private WarehouseFixtures() {}

  static Warehouse warehouse(String businessUnitCode, String location, int capacity, int stock) {
    var warehouse = new Warehouse();
    warehouse.businessUnitCode = businessUnitCode;
    warehouse.location = location;
    warehouse.capacity = capacity;
    warehouse.stock = stock;
    return warehouse;
  }

  /** An already-persisted, active warehouse. */
  static Warehouse existing(String businessUnitCode, String location, int capacity, int stock) {
    var warehouse = warehouse(businessUnitCode, location, capacity, stock);
    warehouse.createdAt = LocalDateTime.now().minusDays(1);
    return warehouse;
  }

  /** An already-persisted warehouse that has been archived. */
  static Warehouse archived(String businessUnitCode, String location, int capacity, int stock) {
    var warehouse = existing(businessUnitCode, location, capacity, stock);
    warehouse.archivedAt = LocalDateTime.now().minusHours(1);
    return warehouse;
  }
}
