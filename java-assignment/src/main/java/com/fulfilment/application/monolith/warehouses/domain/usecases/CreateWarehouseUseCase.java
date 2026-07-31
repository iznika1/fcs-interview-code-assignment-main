package com.fulfilment.application.monolith.warehouses.domain.usecases;

import com.fulfilment.application.monolith.warehouses.domain.models.Warehouse;
import com.fulfilment.application.monolith.warehouses.domain.ports.CreateWarehouseOperation;
import com.fulfilment.application.monolith.warehouses.domain.ports.LocationResolver;
import com.fulfilment.application.monolith.warehouses.domain.ports.WarehouseStore;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.LocalDateTime;

@ApplicationScoped
public class CreateWarehouseUseCase implements CreateWarehouseOperation {

  private final WarehouseStore warehouseStore;
  private final WarehouseRules rules;

  public CreateWarehouseUseCase(WarehouseStore warehouseStore, LocationResolver locationResolver) {
    this.warehouseStore = warehouseStore;
    this.rules = new WarehouseRules(warehouseStore, locationResolver);
  }

  @Override
  public void create(Warehouse warehouse) {
    // the payload must be complete and its stock must fit its own capacity
    rules.validateSelfConsistent(warehouse);

    // the business unit code must not already be used by an active warehouse
    rules.validateBusinessUnitCodeIsFree(warehouse.businessUnitCode);

    // the location must exist and must have room for both this warehouse and its capacity
    rules.validateFitsLocation(warehouse, null);

    if (warehouse.createdAt == null) {
      warehouse.createdAt = LocalDateTime.now();
    }
    // a warehouse is born active; a caller cannot hand us a pre-archived one
    warehouse.archivedAt = null;

    // if all went well, create the warehouse
    warehouseStore.create(warehouse);
  }
}
