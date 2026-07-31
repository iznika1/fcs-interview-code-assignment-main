package com.fulfilment.application.monolith.warehouses.domain.usecases;

import static com.fulfilment.application.monolith.warehouses.domain.usecases.WarehouseFixtures.existing;
import static com.fulfilment.application.monolith.warehouses.domain.usecases.WarehouseFixtures.warehouse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fulfilment.application.monolith.warehouses.domain.exceptions.WarehouseNotFoundException;
import com.fulfilment.application.monolith.warehouses.domain.exceptions.WarehouseValidationException;
import com.fulfilment.application.monolith.warehouses.domain.models.Warehouse;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Unit tests for the replacement rules, against in-memory fakes. */
class ReplaceWarehouseUseCaseTest {

  private InMemoryWarehouseStore store;
  private ReplaceWarehouseUseCase useCase;
  private Warehouse previous;

  @BeforeEach
  void setUp() {
    store = new InMemoryWarehouseStore();
    useCase = new ReplaceWarehouseUseCase(store, new FakeLocationResolver());

    // TILBURG-001 allows a single warehouse of at most 40 capacity; MWH.023 is that warehouse
    previous = existing("MWH.023", "TILBURG-001", 30, 27);
    store.given(previous);
  }

  @Test
  @DisplayName("W13 — the previous warehouse is archived and a new active one reuses its code")
  void archivesThePreviousWarehouseAndCreatesTheNewOne() {
    var replacement = warehouse("MWH.023", "TILBURG-001", 35, 27);

    useCase.replace(replacement);

    assertNotNull(previous.archivedAt, "the replaced warehouse must be archived");
    assertEquals(List.of("MWH.023"), store.updateCalls, "archiving goes through update()");
    assertEquals(List.of("MWH.023"), store.createCalls, "the replacement is a new row");
    assertTrue(store.removeCalls.isEmpty(), "archiving is a soft delete, never a removal");

    assertNull(replacement.archivedAt);
    assertNotNull(replacement.createdAt);

    // the history is kept: two rows share the code, only the replacement is active
    assertEquals(2, store.allRows().size());
    assertSame(replacement, store.findByBusinessUnitCode("MWH.023"));
    assertEquals(1, store.getAll().size());
  }

  @Test
  @DisplayName("W13 — the slot and capacity freed by the replaced warehouse are available again")
  void doesNotCountTheReplacedWarehouseAgainstItsOwnReplacement() {
    // TILBURG-001 is full (1 of 1) and 30 of its 40 capacity is used by MWH.023 itself.
    // A replacement of capacity 40 only fits because the warehouse it replaces steps aside.
    useCase.replace(warehouse("MWH.023", "TILBURG-001", 40, 27));

    assertEquals(List.of("MWH.023"), store.createCalls);
    assertEquals(40, store.findByBusinessUnitCode("MWH.023").capacity);
  }

  @Test
  @DisplayName("W14 — a capacity that cannot hold the previous stock is rejected")
  void rejectsACapacityThatCannotHoldThePreviousStock() {
    var tooSmall = warehouse("MWH.023", "TILBURG-001", 10, 27);

    var thrown = assertThrows(WarehouseValidationException.class, () -> useCase.replace(tooSmall));

    // a stock of 27 does not even fit a capacity of 10 — caught before the codes are compared
    assertTrue(thrown.getMessage().contains("27"), thrown.getMessage());
    assertNothingWasWritten();
  }

  @Test
  @DisplayName("W14 — a capacity below the previous stock is rejected even when self-consistent")
  void rejectsACapacityBelowThePreviousStockEvenIfItFitsItsOwnStock() {
    // capacity 20 comfortably holds a stock of 20, but not the 27 it would inherit
    var tooSmall = warehouse("MWH.023", "TILBURG-001", 20, 20);

    var thrown = assertThrows(WarehouseValidationException.class, () -> useCase.replace(tooSmall));

    assertTrue(thrown.getMessage().contains("cannot accommodate"), thrown.getMessage());
    assertNothingWasWritten();
  }

  @Test
  @DisplayName("W15 — a stock different from the previous stock is rejected")
  void rejectsAStockThatDoesNotMatchThePreviousOne() {
    var wrongStock = warehouse("MWH.023", "TILBURG-001", 35, 5);

    var thrown = assertThrows(WarehouseValidationException.class, () -> useCase.replace(wrongStock));

    assertTrue(thrown.getMessage().contains("must match"), thrown.getMessage());
    assertNothingWasWritten();
  }

  @Test
  @DisplayName("W16 — replacing an unknown business unit code is a not-found error")
  void rejectsAnUnknownBusinessUnitCode() {
    var thrown =
        assertThrows(
            WarehouseNotFoundException.class,
            () -> useCase.replace(warehouse("MWH.999", "TILBURG-001", 20, 5)));

    assertTrue(thrown.getMessage().contains("MWH.999"), thrown.getMessage());
    assertNothingWasWritten();
  }

  @Test
  @DisplayName("W16 — an already archived warehouse is not replaceable")
  void treatsAnAlreadyArchivedWarehouseAsUnknown() {
    previous.archivedAt = LocalDateTime.now();

    assertThrows(
        WarehouseNotFoundException.class,
        () -> useCase.replace(warehouse("MWH.023", "TILBURG-001", 30, 27)));
  }

  @Test
  @DisplayName("the location rules still apply to a replacement, and reject it before any write")
  void stillEnforcesTheLocationCapacity() {
    // TILBURG-001 tops out at 40 even with the replaced warehouse's 30 released
    var tooLarge = warehouse("MWH.023", "TILBURG-001", 45, 27);

    var thrown = assertThrows(WarehouseValidationException.class, () -> useCase.replace(tooLarge));

    assertTrue(thrown.getMessage().contains("remaining capacity of 40"), thrown.getMessage());
    assertNothingWasWritten();
  }

  @Test
  @DisplayName("a replacement into a location that does not exist is rejected")
  void rejectsAnUnknownLocation() {
    assertThrows(
        WarehouseValidationException.class,
        () -> useCase.replace(warehouse("MWH.023", "ATLANTIS-001", 30, 27)));

    assertNothingWasWritten();
  }

  /** A rejected replacement must leave the warehouse it would have replaced active and untouched. */
  private void assertNothingWasWritten() {
    assertNull(previous.archivedAt, "the replaced warehouse must stay active");
    assertTrue(store.createCalls.isEmpty());
    assertTrue(store.updateCalls.isEmpty());
    assertTrue(store.removeCalls.isEmpty());
  }
}
