package com.fulfilment.application.monolith.warehouses.domain.usecases;

import static com.fulfilment.application.monolith.warehouses.domain.usecases.WarehouseFixtures.archived;
import static com.fulfilment.application.monolith.warehouses.domain.usecases.WarehouseFixtures.existing;
import static com.fulfilment.application.monolith.warehouses.domain.usecases.WarehouseFixtures.warehouse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fulfilment.application.monolith.warehouses.domain.exceptions.WarehouseValidationException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the creation rules. Plain JUnit against in-memory fakes: no Quarkus boot, no
 * database, no mocking framework.
 */
class CreateWarehouseUseCaseTest {

  private InMemoryWarehouseStore store;
  private CreateWarehouseUseCase useCase;

  @BeforeEach
  void setUp() {
    store = new InMemoryWarehouseStore();
    useCase = new CreateWarehouseUseCase(store, new FakeLocationResolver());
  }

  @Test
  @DisplayName("W1 — a valid warehouse is stored and stamped as created")
  void createsAValidWarehouse() {
    var newWarehouse = warehouse("MWH.100", "AMSTERDAM-002", 20, 5);

    useCase.create(newWarehouse);

    assertEquals(List.of("MWH.100"), store.createCalls);
    assertNotNull(newWarehouse.createdAt, "createdAt must be stamped");
    assertNull(newWarehouse.archivedAt, "a new warehouse is active");
    assertNotNull(store.findByBusinessUnitCode("MWH.100"));
  }

  @Test
  @DisplayName("W2 — a business unit code already used by an active warehouse is rejected")
  void rejectsADuplicateBusinessUnitCode() {
    store.given(existing("MWH.001", "ZWOLLE-001", 30, 10));

    var duplicate = warehouse("MWH.001", "AMSTERDAM-002", 20, 5);

    var thrown = assertThrows(WarehouseValidationException.class, () -> useCase.create(duplicate));
    assertTrue(thrown.getMessage().contains("MWH.001"), thrown.getMessage());
    assertTrue(store.createCalls.isEmpty(), "nothing may be written when validation fails");
  }

  @Test
  @DisplayName("W2 — a business unit code freed by archiving can be reused")
  void allowsReusingTheCodeOfAnArchivedWarehouse() {
    store.given(archived("MWH.001", "ZWOLLE-001", 30, 10));

    useCase.create(warehouse("MWH.001", "AMSTERDAM-002", 20, 5));

    assertEquals(List.of("MWH.001"), store.createCalls);
  }

  @Test
  @DisplayName("W3 — a location that does not exist is rejected")
  void rejectsAnUnknownLocation() {
    var thrown =
        assertThrows(
            WarehouseValidationException.class,
            () -> useCase.create(warehouse("MWH.100", "ATLANTIS-001", 20, 5)));

    assertTrue(thrown.getMessage().contains("ATLANTIS-001"), thrown.getMessage());
    assertTrue(store.createCalls.isEmpty());
  }

  @Test
  @DisplayName("W4 — a location that already holds its maximum number of warehouses is rejected")
  void rejectsALocationAtItsWarehouseLimit() {
    // TILBURG-001 allows a single warehouse, and MWH.023 is it
    store.given(existing("MWH.023", "TILBURG-001", 30, 27));

    var thrown =
        assertThrows(
            WarehouseValidationException.class,
            () -> useCase.create(warehouse("MWH.100", "TILBURG-001", 5, 1)));

    assertTrue(thrown.getMessage().contains("maximum"), thrown.getMessage());
    assertTrue(store.createCalls.isEmpty());
  }

  @Test
  @DisplayName("W4 — archived warehouses do not occupy a slot")
  void ignoresArchivedWarehousesWhenCountingSlots() {
    store.given(archived("MWH.023", "TILBURG-001", 30, 27));

    useCase.create(warehouse("MWH.100", "TILBURG-001", 5, 1));

    assertEquals(List.of("MWH.100"), store.createCalls);
  }

  @Test
  @DisplayName("W5 — a capacity beyond the location's maximum capacity is rejected")
  void rejectsACapacityBeyondTheLocationMaximum() {
    // AMSTERDAM-002 is empty, has 3 free slots and a maximum capacity of 75
    var thrown =
        assertThrows(
            WarehouseValidationException.class,
            () -> useCase.create(warehouse("MWH.100", "AMSTERDAM-002", 80, 5)));

    assertTrue(thrown.getMessage().contains("capacity"), thrown.getMessage());
    assertTrue(store.createCalls.isEmpty());
  }

  @Test
  @DisplayName("W5 — the capacity already used at the location counts towards the maximum")
  void rejectsACapacityThatOverflowsTheLocationTogetherWithItsNeighbours() {
    // ZWOLLE-002 allows 2 warehouses and a total capacity of 50; 30 is already taken
    store.given(existing("MWH.200", "ZWOLLE-002", 30, 10));

    var thrown =
        assertThrows(
            WarehouseValidationException.class,
            () -> useCase.create(warehouse("MWH.201", "ZWOLLE-002", 25, 5)));

    assertTrue(thrown.getMessage().contains("remaining capacity of 20"), thrown.getMessage());

    // and the very same warehouse fits once it stays within the headroom
    useCase.create(warehouse("MWH.201", "ZWOLLE-002", 20, 5));
    assertEquals(List.of("MWH.201"), store.createCalls);
  }

  @Test
  @DisplayName("W6 — stock larger than the warehouse's own capacity is rejected")
  void rejectsStockThatDoesNotFitTheCapacity() {
    var thrown =
        assertThrows(
            WarehouseValidationException.class,
            () -> useCase.create(warehouse("MWH.100", "AMSTERDAM-002", 10, 50)));

    assertTrue(thrown.getMessage().contains("does not fit"), thrown.getMessage());
    assertTrue(store.createCalls.isEmpty());
  }

  @Test
  @DisplayName("an incomplete payload is a validation error, not a crash")
  void rejectsIncompletePayloads() {
    assertThrows(WarehouseValidationException.class, () -> useCase.create(null));
    assertThrows(
        WarehouseValidationException.class,
        () -> useCase.create(warehouse(null, "AMSTERDAM-002", 20, 5)));
    assertThrows(
        WarehouseValidationException.class, () -> useCase.create(warehouse("MWH.100", null, 20, 5)));

    var noCapacity = warehouse("MWH.100", "AMSTERDAM-002", 20, 5);
    noCapacity.capacity = null;
    assertThrows(WarehouseValidationException.class, () -> useCase.create(noCapacity));

    var noStock = warehouse("MWH.100", "AMSTERDAM-002", 20, 5);
    noStock.stock = null;
    assertThrows(WarehouseValidationException.class, () -> useCase.create(noStock));

    assertTrue(store.createCalls.isEmpty());
  }
}
