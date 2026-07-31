package com.fulfilment.application.monolith.fulfilment;

/**
 * Raised when a fulfilment association does not exist.
 *
 * <p>Maps to HTTP 404 in {@link FulfilmentResource}.
 */
public class FulfilmentNotFoundException extends RuntimeException {

  public FulfilmentNotFoundException(Long id) {
    super("Fulfilment with id of " + id + " does not exist.");
  }
}
