package com.fulfilment.application.monolith.warehouses.adapters.restapi;

import com.fulfilment.application.monolith.warehouses.domain.exceptions.WarehouseNotFoundException;
import com.fulfilment.application.monolith.warehouses.domain.ports.ArchiveWarehouseOperation;
import com.fulfilment.application.monolith.warehouses.domain.ports.CreateWarehouseOperation;
import com.fulfilment.application.monolith.warehouses.domain.ports.ReplaceWarehouseOperation;
import com.fulfilment.application.monolith.warehouses.domain.ports.WarehouseStore;
import com.warehouse.api.WarehouseResource;
import com.warehouse.api.beans.Warehouse;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * REST adapter for the warehouse domain. It only translates HTTP to the inbound ports and back — no
 * business rule lives here; those belong to the use cases.
 *
 * <p>The {@code {id}} path parameter is the <b>business unit code</b>, not the database primary key
 * (contract C4): the domain model has no id at all, and the sibling replacement path already keys on
 * the business unit code.
 */
@RequestScoped
public class WarehouseResourceImpl implements WarehouseResource {

  @Inject WarehouseStore warehouseStore;

  @Inject CreateWarehouseOperation createWarehouseOperation;

  @Inject ReplaceWarehouseOperation replaceWarehouseOperation;

  @Inject ArchiveWarehouseOperation archiveWarehouseOperation;

  @Override
  public List<Warehouse> listAllWarehousesUnits() {
    return warehouseStore.getAll().stream().map(this::toWarehouseResponse).toList();
  }

  // The spec declares 201 here, but the generated interface returns the bean directly and carries no
  // status metadata, so the framework would answer 200. WarehouseCreatedStatusFilter restores it.
  @Override
  @Transactional
  public Warehouse createANewWarehouseUnit(@NotNull Warehouse data) {
    var warehouse = toWarehouse(data);
    createWarehouseOperation.create(warehouse);

    return toWarehouseResponse(warehouse);
  }

  @Override
  public Warehouse getAWarehouseUnitByID(String id) {
    return toWarehouseResponse(getActiveOrThrow(id));
  }

  @Override
  @Transactional
  public void archiveAWarehouseUnitByID(String id) {
    archiveWarehouseOperation.archive(getActiveOrThrow(id));
  }

  @Override
  @Transactional
  public Warehouse replaceTheCurrentActiveWarehouse(
      String businessUnitCode, @NotNull Warehouse data) {
    var replacement = toWarehouse(data);
    // The path is authoritative: a replacement always reuses the code it replaces.
    replacement.businessUnitCode = businessUnitCode;

    replaceWarehouseOperation.replace(replacement);

    return toWarehouseResponse(replacement);
  }

  /** Reads the active warehouse for a business unit code, or fails with a 404-mapped exception. */
  private com.fulfilment.application.monolith.warehouses.domain.models.Warehouse getActiveOrThrow(
      String businessUnitCode) {
    var warehouse = warehouseStore.findByBusinessUnitCode(businessUnitCode);
    if (warehouse == null) {
      throw new WarehouseNotFoundException(businessUnitCode);
    }
    return warehouse;
  }

  private Warehouse toWarehouseResponse(
      com.fulfilment.application.monolith.warehouses.domain.models.Warehouse warehouse) {
    var response = new Warehouse();
    response.setBusinessUnitCode(warehouse.businessUnitCode);
    response.setLocation(warehouse.location);
    response.setCapacity(warehouse.capacity);
    response.setStock(warehouse.stock);

    return response;
  }

  private com.fulfilment.application.monolith.warehouses.domain.models.Warehouse toWarehouse(
      Warehouse data) {
    var warehouse = new com.fulfilment.application.monolith.warehouses.domain.models.Warehouse();
    warehouse.businessUnitCode = data.getBusinessUnitCode();
    warehouse.location = data.getLocation();
    warehouse.capacity = data.getCapacity();
    warehouse.stock = data.getStock();

    return warehouse;
  }
}
