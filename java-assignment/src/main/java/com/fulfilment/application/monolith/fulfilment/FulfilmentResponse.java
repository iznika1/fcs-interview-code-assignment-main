package com.fulfilment.application.monolith.fulfilment;

/**
 * Response payload for a fulfilment association.
 *
 * <p>Kept separate from the {@link Fulfilment} entity so the persisted shape can evolve without
 * changing the published API.
 */
public record FulfilmentResponse(
    Long id, Long storeId, Long productId, String warehouseBusinessUnitCode) {

  static FulfilmentResponse from(Fulfilment fulfilment) {
    return new FulfilmentResponse(
        fulfilment.id,
        fulfilment.storeId,
        fulfilment.productId,
        fulfilment.warehouseBusinessUnitCode);
  }
}
