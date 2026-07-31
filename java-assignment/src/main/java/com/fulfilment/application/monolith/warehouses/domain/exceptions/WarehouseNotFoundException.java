package com.fulfilment.application.monolith.warehouses.domain.exceptions;

/**
 * Raised when no active warehouse exists for a given business unit code.
 *
 * <p>Maps to HTTP 404 in the REST adapter. Deliberately free of any JAX-RS or JPA type so the domain
 * layer stays independent of its adapters.
 */
public class WarehouseNotFoundException extends RuntimeException {

  public WarehouseNotFoundException(String businessUnitCode) {
    super("Warehouse with business unit code '" + businessUnitCode + "' does not exist.");
  }
}
