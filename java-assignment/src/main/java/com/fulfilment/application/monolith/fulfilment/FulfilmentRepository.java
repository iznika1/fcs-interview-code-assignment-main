package com.fulfilment.application.monolith.fulfilment;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Persistence for {@link Fulfilment}, following the repository style used by {@code products} rather
 * than the active-record style used by {@code stores}, so the rules can be exercised against an
 * injected collaborator.
 *
 * <p>The distinct-set queries are deliberately expressed in Java rather than JPQL: the row counts
 * here are bounded by the business rules themselves (at most 3 warehouses per store, 5 products per
 * warehouse), so clarity is worth more than pushing the projection into SQL.
 */
@ApplicationScoped
public class FulfilmentRepository implements PanacheRepository<Fulfilment> {

  /** Warehouses already fulfilling a given product in a given store. */
  public Set<String> warehousesFor(Long storeId, Long productId) {
    return find("storeId = ?1 and productId = ?2", storeId, productId).stream()
        .map(fulfilment -> fulfilment.warehouseBusinessUnitCode)
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  /** Distinct warehouses fulfilling anything at all in a given store. */
  public Set<String> warehousesForStore(Long storeId) {
    return find("storeId", storeId).stream()
        .map(fulfilment -> fulfilment.warehouseBusinessUnitCode)
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  /** Distinct product types stored by a given warehouse, across every store. */
  public Set<Long> productsForWarehouse(String warehouseBusinessUnitCode) {
    return find("warehouseBusinessUnitCode", warehouseBusinessUnitCode).stream()
        .map(fulfilment -> fulfilment.productId)
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  public Fulfilment findAssociation(Long storeId, Long productId, String warehouseBusinessUnitCode) {
    return find(
            "storeId = ?1 and productId = ?2 and warehouseBusinessUnitCode = ?3",
            storeId,
            productId,
            warehouseBusinessUnitCode)
        .firstResult();
  }
}
