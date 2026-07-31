package com.fulfilment.application.monolith.warehouses.domain.usecases;

import com.fulfilment.application.monolith.warehouses.domain.exceptions.WarehouseNotFoundException;
import com.fulfilment.application.monolith.warehouses.domain.exceptions.WarehouseValidationException;
import com.fulfilment.application.monolith.warehouses.domain.models.Warehouse;
import com.fulfilment.application.monolith.warehouses.domain.ports.LocationResolver;
import com.fulfilment.application.monolith.warehouses.domain.ports.ReplaceWarehouseOperation;
import com.fulfilment.application.monolith.warehouses.domain.ports.WarehouseStore;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.LocalDateTime;

@ApplicationScoped
public class ReplaceWarehouseUseCase implements ReplaceWarehouseOperation {

  private final WarehouseStore warehouseStore;
  private final WarehouseRules rules;

  public ReplaceWarehouseUseCase(WarehouseStore warehouseStore, LocationResolver locationResolver) {
    this.warehouseStore = warehouseStore;
    this.rules = new WarehouseRules(warehouseStore, locationResolver);
  }

  /**
   * Archives the warehouse currently holding {@code newWarehouse.businessUnitCode} and creates a
   * new active one under that same code, so the history of the business unit is preserved.
   *
   * <p>Every validation runs before anything is written: a rejected replacement must leave the
   * warehouse being replaced untouched and still active.
   */
  @Override
  public void replace(Warehouse newWarehouse) {
    rules.validateSelfConsistent(newWarehouse);

    var previous = warehouseStore.findByBusinessUnitCode(newWarehouse.businessUnitCode);
    if (previous == null) {
      throw new WarehouseNotFoundException(newWarehouse.businessUnitCode);
    }

    int previousStock = previous.stock == null ? 0 : previous.stock;

    // the replacement must be able to accommodate the stock it inherits
    if (newWarehouse.capacity < previousStock) {
      throw new WarehouseValidationException(
          "A capacity of "
              + newWarehouse.capacity
              + " cannot accommodate the stock of "
              + previousStock
              + " held by warehouse '"
              + previous.businessUnitCode
              + "'.");
    }

    // and the stock has to be carried over unchanged
    if (newWarehouse.stock != previousStock) {
      throw new WarehouseValidationException(
          "The stock of "
              + newWarehouse.stock
              + " must match the stock of "
              + previousStock
              + " held by warehouse '"
              + previous.businessUnitCode
              + "'.");
    }

    // The location limits still apply, but the warehouse being replaced releases its slot and its
    // capacity in the same operation, so it must not be counted against its own replacement.
    rules.validateFitsLocation(newWarehouse, previous.businessUnitCode);

    var now = LocalDateTime.now();

    // archive first: the code is only free for reuse once the previous warehouse is inactive
    previous.archivedAt = now;
    warehouseStore.update(previous);

    newWarehouse.createdAt = now;
    newWarehouse.archivedAt = null;
    warehouseStore.create(newWarehouse);
  }
}
