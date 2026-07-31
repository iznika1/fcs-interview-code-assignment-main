package com.fulfilment.application.monolith.warehouses.domain.usecases;

import com.fulfilment.application.monolith.warehouses.domain.exceptions.WarehouseValidationException;
import com.fulfilment.application.monolith.warehouses.domain.models.Warehouse;
import com.fulfilment.application.monolith.warehouses.domain.ports.LocationResolver;
import com.fulfilment.application.monolith.warehouses.domain.ports.WarehouseStore;
import java.util.List;
import java.util.Objects;

/**
 * Business rules shared by warehouse creation and replacement.
 *
 * <p>Package-private on purpose: this is an implementation detail of the use cases, not a port. It
 * is a plain object rather than a CDI bean so the use cases can build it in their constructor and
 * the unit tests can drive it from in-memory fakes.
 */
final class WarehouseRules {

  private final WarehouseStore warehouseStore;
  private final LocationResolver locationResolver;

  WarehouseRules(WarehouseStore warehouseStore, LocationResolver locationResolver) {
    this.warehouseStore = warehouseStore;
    this.locationResolver = locationResolver;
  }

  /** Rejects an incomplete payload and stock that does not fit the warehouse's own capacity. */
  void validateSelfConsistent(Warehouse warehouse) {
    if (warehouse == null) {
      throw new WarehouseValidationException("A warehouse must be provided.");
    }
    if (isBlank(warehouse.businessUnitCode)) {
      throw new WarehouseValidationException("The business unit code is required.");
    }
    if (isBlank(warehouse.location)) {
      throw new WarehouseValidationException("The location is required.");
    }
    if (warehouse.capacity == null || warehouse.capacity <= 0) {
      throw new WarehouseValidationException("The capacity must be a positive number.");
    }
    if (warehouse.stock == null || warehouse.stock < 0) {
      throw new WarehouseValidationException("The stock must be zero or a positive number.");
    }
    if (warehouse.stock > warehouse.capacity) {
      throw new WarehouseValidationException(
          "The stock of "
              + warehouse.stock
              + " does not fit the capacity of "
              + warehouse.capacity
              + ".");
    }
  }

  /** Rejects a business unit code that is already taken by an <em>active</em> warehouse. */
  void validateBusinessUnitCodeIsFree(String businessUnitCode) {
    if (warehouseStore.findByBusinessUnitCode(businessUnitCode) != null) {
      throw new WarehouseValidationException(
          "An active warehouse with business unit code '" + businessUnitCode + "' already exists.");
    }
  }

  /**
   * Checks the warehouse against the limits of the location it claims.
   *
   * <p>{@code warehouseStore.getAll()} is active-only, so archived warehouses never consume a slot
   * or capacity.
   *
   * @param freedBusinessUnitCode business unit code of a warehouse that is about to be archived as
   *     part of the same operation, and whose slot and capacity therefore must not count against
   *     the limits; {@code null} for a plain creation.
   */
  void validateFitsLocation(Warehouse warehouse, String freedBusinessUnitCode) {
    var location = locationResolver.resolveByIdentifier(warehouse.location);
    if (location == null) {
      throw new WarehouseValidationException(
          "Location '" + warehouse.location + "' does not exist.");
    }

    List<Warehouse> occupants =
        warehouseStore.getAll().stream()
            .filter(existing -> warehouse.location.equals(existing.location))
            .filter(existing -> !Objects.equals(freedBusinessUnitCode, existing.businessUnitCode))
            .toList();

    if (occupants.size() >= location.maxNumberOfWarehouses) {
      throw new WarehouseValidationException(
          "Location '"
              + warehouse.location
              + "' already holds its maximum of "
              + location.maxNumberOfWarehouses
              + " warehouse(s).");
    }

    int usedCapacity =
        occupants.stream().mapToInt(existing -> existing.capacity == null ? 0 : existing.capacity).sum();
    if (usedCapacity + warehouse.capacity > location.maxCapacity) {
      throw new WarehouseValidationException(
          "A capacity of "
              + warehouse.capacity
              + " exceeds the remaining capacity of "
              + (location.maxCapacity - usedCapacity)
              + " at location '"
              + warehouse.location
              + "'.");
    }
  }

  static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
