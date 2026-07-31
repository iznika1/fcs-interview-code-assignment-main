package com.fulfilment.application.monolith.fulfilment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fulfilment.application.monolith.products.Product;
import com.fulfilment.application.monolith.products.ProductRepository;
import com.fulfilment.application.monolith.warehouses.domain.models.Warehouse;
import com.fulfilment.application.monolith.warehouses.domain.ports.WarehouseStore;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/**
 * Covers the three fulfilment caps (acceptance rows B1-B3) plus the reference and duplicate checks.
 *
 * <p>Every test is {@code @TestTransaction}, so the rows it creates roll back and the seeded data
 * other test classes rely on is never mutated.
 */
@QuarkusTest
public class FulfilmentServiceTest {

  private static final long STORE_TONSTAD = 1L;
  private static final long STORE_KALLAX = 2L;

  @Inject FulfilmentService fulfilmentService;
  @Inject ProductRepository productRepository;
  @Inject WarehouseStore warehouseStore;

  // --- B1: a product may be fulfilled by at most 2 warehouses per store ---

  @Test
  @TestTransaction
  public void rejectsAThirdWarehouseForTheSameProductAndStore() {
    String third = givenWarehouse("FUL.B1", "ZWOLLE-002", 10);

    fulfilmentService.associate(STORE_TONSTAD, 1L, "MWH.001");
    fulfilmentService.associate(STORE_TONSTAD, 1L, "MWH.012");

    var failure =
        assertThrows(
            FulfilmentValidationException.class,
            () -> fulfilmentService.associate(STORE_TONSTAD, 1L, third));

    assertTrue(
        failure.getMessage().contains("maximum of 2 warehouses"),
        "unexpected message: " + failure.getMessage());
  }

  @Test
  @TestTransaction
  public void allowsTwoWarehousesForTheSameProductAndStore() {
    fulfilmentService.associate(STORE_TONSTAD, 1L, "MWH.001");
    fulfilmentService.associate(STORE_TONSTAD, 1L, "MWH.012");

    assertEquals(2, fulfilmentService.listForStore(STORE_TONSTAD).size());
  }

  // --- B2: a store may be fulfilled by at most 3 warehouses ---

  @Test
  @TestTransaction
  public void rejectsAFourthWarehouseForTheSameStore() {
    String fourth = givenWarehouse("FUL.B2", "ZWOLLE-002", 10);

    // Three distinct warehouses, spread over products so the per-product cap never fires first.
    fulfilmentService.associate(STORE_TONSTAD, 1L, "MWH.001");
    fulfilmentService.associate(STORE_TONSTAD, 1L, "MWH.012");
    fulfilmentService.associate(STORE_TONSTAD, 2L, "MWH.023");

    var failure =
        assertThrows(
            FulfilmentValidationException.class,
            () -> fulfilmentService.associate(STORE_TONSTAD, 2L, fourth));

    assertTrue(
        failure.getMessage().contains("maximum of 3 warehouses"),
        "unexpected message: " + failure.getMessage());
  }

  @Test
  @TestTransaction
  public void reusingAWarehouseTheStoreAlreadyHasDoesNotConsumeASlot() {
    fulfilmentService.associate(STORE_TONSTAD, 1L, "MWH.001");
    fulfilmentService.associate(STORE_TONSTAD, 1L, "MWH.012");
    fulfilmentService.associate(STORE_TONSTAD, 2L, "MWH.023");

    // A fourth association, but on a warehouse the store already uses - must be allowed.
    fulfilmentService.associate(STORE_TONSTAD, 2L, "MWH.001");

    assertEquals(4, fulfilmentService.listForStore(STORE_TONSTAD).size());
  }

  // --- B3: a warehouse may store at most 5 product types ---

  @Test
  @TestTransaction
  public void rejectsASixthProductTypeInTheSameWarehouse() {
    long[] products = {1L, 2L, 3L, givenProduct("FUL-P4"), givenProduct("FUL-P5")};
    for (long productId : products) {
      fulfilmentService.associate(STORE_TONSTAD, productId, "MWH.001");
    }

    long sixth = givenProduct("FUL-P6");

    var failure =
        assertThrows(
            FulfilmentValidationException.class,
            () -> fulfilmentService.associate(STORE_TONSTAD, sixth, "MWH.001"));

    assertTrue(
        failure.getMessage().contains("maximum of 5 product types"),
        "unexpected message: " + failure.getMessage());
  }

  @Test
  @TestTransaction
  public void countsProductTypesPerWarehouseAcrossStores() {
    long[] products = {1L, 2L, 3L, givenProduct("FUL-X4"), givenProduct("FUL-X5")};
    for (long productId : products) {
      fulfilmentService.associate(STORE_TONSTAD, productId, "MWH.001");
    }

    long sixth = givenProduct("FUL-X6");

    // A different store must not reset the warehouse's product-type budget.
    assertThrows(
        FulfilmentValidationException.class,
        () -> fulfilmentService.associate(STORE_KALLAX, sixth, "MWH.001"));
  }

  // --- references and duplicates ---

  @Test
  @TestTransaction
  public void rejectsAnUnknownStore() {
    assertThrows(
        FulfilmentValidationException.class,
        () -> fulfilmentService.associate(9999L, 1L, "MWH.001"));
  }

  @Test
  @TestTransaction
  public void rejectsAnUnknownProduct() {
    assertThrows(
        FulfilmentValidationException.class,
        () -> fulfilmentService.associate(STORE_TONSTAD, 9999L, "MWH.001"));
  }

  @Test
  @TestTransaction
  public void rejectsAnUnknownWarehouse() {
    assertThrows(
        FulfilmentValidationException.class,
        () -> fulfilmentService.associate(STORE_TONSTAD, 1L, "MWH.999"));
  }

  @Test
  @TestTransaction
  public void rejectsADuplicateAssociation() {
    fulfilmentService.associate(STORE_TONSTAD, 1L, "MWH.001");

    var failure =
        assertThrows(
            FulfilmentValidationException.class,
            () -> fulfilmentService.associate(STORE_TONSTAD, 1L, "MWH.001"));

    assertTrue(
        failure.getMessage().contains("already fulfils"),
        "unexpected message: " + failure.getMessage());
  }

  @Test
  @TestTransaction
  public void removingAnUnknownAssociationIsNotFound() {
    assertThrows(FulfilmentNotFoundException.class, () -> fulfilmentService.remove(9999L));
  }

  // --- helpers ---

  /** Creates an active warehouse directly through the store port and returns its code. */
  private String givenWarehouse(String businessUnitCode, String location, int capacity) {
    var warehouse = new Warehouse();
    warehouse.businessUnitCode = businessUnitCode;
    warehouse.location = location;
    warehouse.capacity = capacity;
    warehouse.stock = 0;
    warehouse.createdAt = LocalDateTime.now();
    warehouseStore.create(warehouse);
    return businessUnitCode;
  }

  private long givenProduct(String name) {
    var product = new Product(name);
    productRepository.persist(product);
    return product.id;
  }
}
