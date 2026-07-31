package com.fulfilment.application.monolith.fulfilment;

/**
 * Raised when a fulfilment association would violate a business rule.
 *
 * <p>Maps to HTTP 400 in {@link FulfilmentResource}. Carries no JAX-RS type so the rules stay
 * testable without a web layer.
 */
public class FulfilmentValidationException extends RuntimeException {

  public FulfilmentValidationException(String message) {
    super(message);
  }
}
