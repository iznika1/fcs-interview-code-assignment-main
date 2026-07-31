package com.fulfilment.application.monolith.stores;

/**
 * Raised inside the transaction that changed a {@link Store} and observed once that transaction has
 * committed, so the legacy system is never told about a change that was later rolled back.
 *
 * <p>The payload is a detached snapshot taken while the entity is still managed: the observer runs
 * after the persistence context is closed, so it must not touch a managed entity.
 */
public record StoreChangedEvent(Operation operation, Store store) {

  public enum Operation {
    CREATED,
    UPDATED
  }

  public static StoreChangedEvent created(Store persisted) {
    return new StoreChangedEvent(Operation.CREATED, snapshotOf(persisted));
  }

  public static StoreChangedEvent updated(Store persisted) {
    return new StoreChangedEvent(Operation.UPDATED, snapshotOf(persisted));
  }

  private static Store snapshotOf(Store persisted) {
    Store snapshot = new Store(persisted.name);
    snapshot.id = persisted.id;
    snapshot.quantityProductsInStock = persisted.quantityProductsInStock;
    return snapshot;
  }
}
