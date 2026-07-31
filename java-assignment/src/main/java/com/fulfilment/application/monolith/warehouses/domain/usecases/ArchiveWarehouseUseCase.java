package com.fulfilment.application.monolith.warehouses.domain.usecases;

import com.fulfilment.application.monolith.warehouses.domain.exceptions.WarehouseNotFoundException;
import com.fulfilment.application.monolith.warehouses.domain.exceptions.WarehouseValidationException;
import com.fulfilment.application.monolith.warehouses.domain.models.Warehouse;
import com.fulfilment.application.monolith.warehouses.domain.ports.ArchiveWarehouseOperation;
import com.fulfilment.application.monolith.warehouses.domain.ports.WarehouseStore;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.LocalDateTime;

@ApplicationScoped
public class ArchiveWarehouseUseCase implements ArchiveWarehouseOperation {

  private final WarehouseStore warehouseStore;

  public ArchiveWarehouseUseCase(WarehouseStore warehouseStore) {
    this.warehouseStore = warehouseStore;
  }

  /**
   * Soft-deletes the active warehouse holding the given business unit code by stamping {@code
   * archivedAt}. The row is never removed: keeping it is what makes the history of a replaced
   * business unit code readable.
   */
  @Override
  public void archive(Warehouse warehouse) {
    if (warehouse == null || WarehouseRules.isBlank(warehouse.businessUnitCode)) {
      throw new WarehouseValidationException(
          "A warehouse with a business unit code must be provided.");
    }

    var stored = warehouseStore.findByBusinessUnitCode(warehouse.businessUnitCode);
    if (stored == null) {
      throw new WarehouseNotFoundException(warehouse.businessUnitCode);
    }

    var now = LocalDateTime.now();
    stored.archivedAt = now;
    // keep the caller's view in sync when it handed us a different instance
    warehouse.archivedAt = now;

    warehouseStore.update(stored);
  }
}
