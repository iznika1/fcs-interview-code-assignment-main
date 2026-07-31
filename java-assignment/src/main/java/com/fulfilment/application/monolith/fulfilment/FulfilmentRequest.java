package com.fulfilment.application.monolith.fulfilment;

/**
 * Request payload for creating a fulfilment association.
 *
 * <p>Deliberately not the {@link Fulfilment} entity. Exposing a JPA entity as the wire contract ties
 * the API to the schema, so any column added later silently changes the published payload — the
 * problem this codebase already has where {@code ProductResource} returns {@code List<Product>}. A
 * request record also removes the need to reject a client-supplied {@code id}, because there is
 * nowhere to put one.
 */
public record FulfilmentRequest(Long storeId, Long productId, String warehouseBusinessUnitCode) {}
