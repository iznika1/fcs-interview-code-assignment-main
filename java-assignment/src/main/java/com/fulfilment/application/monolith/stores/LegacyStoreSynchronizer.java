package com.fulfilment.application.monolith.stores;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.inject.Inject;

/**
 * Forwards store changes to the legacy system, but only after the transaction that produced them
 * has committed. A transactional observer is used instead of an inline call so that a rollback
 * leaves the legacy system untouched.
 */
@ApplicationScoped
public class LegacyStoreSynchronizer {

  @Inject LegacyStoreManagerGateway legacyStoreManagerGateway;

  void onStoreChanged(@Observes(during = TransactionPhase.AFTER_SUCCESS) StoreChangedEvent event) {
    switch (event.operation()) {
      case CREATED -> legacyStoreManagerGateway.createStoreOnLegacySystem(event.store());
      case UPDATED -> legacyStoreManagerGateway.updateStoreOnLegacySystem(event.store());
    }
  }
}
