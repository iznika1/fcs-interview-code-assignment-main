package com.fulfilment.application.monolith.warehouses.domain.usecases;

import static com.fulfilment.application.monolith.warehouses.domain.usecases.WarehouseFixtures.existing;
import static com.fulfilment.application.monolith.warehouses.domain.usecases.WarehouseFixtures.warehouse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fulfilment.application.monolith.warehouses.domain.exceptions.WarehouseNotFoundException;
import com.fulfilment.application.monolith.warehouses.domain.exceptions.WarehouseValidationException;
import com.fulfilment.application.monolith.warehouses.domain.models.Warehouse;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Unit tests for archiving, against in-memory fakes. */
class ArchiveWarehouseUseCaseTest {

  private InMemoryWarehouseStore store;
  private ArchiveWarehouseUseCase useCase;
  private Warehouse stored;

  @BeforeEach
  void setUp() {
    store = new InMemoryWarehouseStore();
    useCase = new ArchiveWarehouseUseCase(store);

    stored = existing("MWH.001", "ZWOLLE-001", 30, 10);
    store.given(stored);
  }

  @Test
  @DisplayName("archiving stamps archivedAt and persists through update()")
  void archivesTheWarehouse() {
    useCase.archive(stored);

    assertNotNull(stored.archivedAt, "archivedAt must be stamped");
    assertEquals(List.of("MWH.001"), store.updateCalls);
  }

  @Test
  @DisplayName("archiving is a soft delete: the row survives but leaves the active list")
  void keepsTheRowAsHistory() {
    useCase.archive(stored);

    assertTrue(store.removeCalls.isEmpty(), "remove() is a hard delete and must never be called");
    assertEquals(1, store.allRows().size(), "the row is kept as history");
    assertTrue(store.getAll().isEmpty(), "archived warehouses are not listed");
    assertNull(store.findByBusinessUnitCode("MWH.001"), "and are no longer the active warehouse");
  }

  @Test
  @DisplayName("archiving an unknown business unit code is a not-found error")
  void rejectsAnUnknownBusinessUnitCode() {
    var thrown =
        assertThrows(
            WarehouseNotFoundException.class,
            () -> useCase.archive(warehouse("MWH.999", "ZWOLLE-001", 10, 1)));

    assertTrue(thrown.getMessage().contains("MWH.999"), thrown.getMessage());
    assertTrue(store.updateCalls.isEmpty());
  }

  @Test
  @DisplayName("archiving an already archived warehouse is a not-found error")
  void rejectsAnAlreadyArchivedWarehouse() {
    useCase.archive(stored);
    store.updateCalls.clear();

    assertThrows(WarehouseNotFoundException.class, () -> useCase.archive(stored));
    assertTrue(store.updateCalls.isEmpty());
  }

  @Test
  @DisplayName("a warehouse without a business unit code is a validation error, not a crash")
  void rejectsAWarehouseWithoutABusinessUnitCode() {
    assertThrows(WarehouseValidationException.class, () -> useCase.archive(null));
    assertThrows(
        WarehouseValidationException.class,
        () -> useCase.archive(warehouse(null, "ZWOLLE-001", 10, 1)));

    assertTrue(store.updateCalls.isEmpty());
  }
}
