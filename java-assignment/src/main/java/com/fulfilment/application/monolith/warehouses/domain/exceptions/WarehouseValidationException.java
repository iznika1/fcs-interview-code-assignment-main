package com.fulfilment.application.monolith.warehouses.domain.exceptions;

/**
 * Raised when a warehouse operation violates a business rule.
 *
 * <p>Maps to HTTP 400 in the REST adapter. Deliberately free of any JAX-RS or JPA type so the domain
 * layer stays independent of its adapters.
 */
public class WarehouseValidationException extends RuntimeException {

  public WarehouseValidationException(String message) {
    super(message);
  }
}
