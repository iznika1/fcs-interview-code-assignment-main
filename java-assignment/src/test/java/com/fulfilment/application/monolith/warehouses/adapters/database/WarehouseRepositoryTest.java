package com.fulfilment.application.monolith.warehouses.adapters.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fulfilment.application.monolith.warehouses.domain.models.Warehouse;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/**
 * Persistence-level behaviour of the {@code WarehouseStore} adapter. Every test runs inside {@link
 * TestTransaction} so the seeded rows (MWH.001 / MWH.012 / MWH.023) are restored on rollback and
 * nothing leaks into the other test classes.
 */
@QuarkusTest
public class WarehouseRepositoryTest {

  @Inject WarehouseRepository warehouseRepository;

  /** W11 — archiving is a soft delete: the row survives with archivedAt set. */
  @Test
  @TestTransaction
  public void testArchivedWarehouseRowIsKeptWithArchivedAtSet() {
    // given
    var warehouse = warehouseRepository.findByBusinessUnitCode("MWH.001");
    assertNotNull(warehouse);
    var archivedAt = LocalDateTime.now();

    // when
    warehouse.archivedAt = archivedAt;
    warehouseRepository.update(warehouse);
    forceDatabaseRoundTrip();

    // then — the row is still there, and it still carries its history
    var row = warehouseRepository.find("businessUnitCode", "MWH.001").firstResult();
    assertNotNull(row, "archiving must not delete the row");
    assertNotNull(row.archivedAt, "archivedAt must have been persisted");
    assertEquals("ZWOLLE-001", row.location);
    assertEquals(100, row.capacity);
    assertEquals(10, row.stock);
  }

  /** W12 — the store only exposes active warehouses. */
  @Test
  @TestTransaction
  public void testGetAllExcludesArchivedWarehouses() {
    // given
    assertTrue(businessUnitCodesOfAll().contains("MWH.001"));

    // when
    var warehouse = warehouseRepository.findByBusinessUnitCode("MWH.001");
    warehouse.archivedAt = LocalDateTime.now();
    warehouseRepository.update(warehouse);
    forceDatabaseRoundTrip();

    // then
    var codes = businessUnitCodesOfAll();
    assertFalse(codes.contains("MWH.001"), "archived warehouses must not be listed");
    assertTrue(codes.contains("MWH.012"));
    assertTrue(codes.contains("MWH.023"));
  }

  /** C2 — an archived row must not shadow the active one that reuses its business unit code. */
  @Test
  @TestTransaction
  public void testFindByBusinessUnitCodeReturnsTheActiveRowOnly() {
    // given a replaced warehouse: one archived row and one active row on the same code
    var archived = new DbWarehouse();
    archived.businessUnitCode = "MWH.900";
    archived.location = "AMSTERDAM-002";
    archived.capacity = 20;
    archived.stock = 5;
    archived.createdAt = LocalDateTime.now().minusDays(2);
    archived.archivedAt = LocalDateTime.now().minusDays(1);
    warehouseRepository.persist(archived);

    var replacement = new Warehouse();
    replacement.businessUnitCode = "MWH.900";
    replacement.location = "AMSTERDAM-002";
    replacement.capacity = 30;
    replacement.stock = 5;
    warehouseRepository.create(replacement);
    forceDatabaseRoundTrip();

    // when
    var found = warehouseRepository.findByBusinessUnitCode("MWH.900");

    // then
    assertNotNull(found);
    assertEquals(30, found.capacity);
    assertNull(found.archivedAt);
    assertEquals(2, warehouseRepository.find("businessUnitCode", "MWH.900").count());
  }

  @Test
  @TestTransaction
  public void testFindByUnknownBusinessUnitCodeReturnsNull() {
    assertNull(warehouseRepository.findByBusinessUnitCode("MWH.999"));
  }

  @Test
  @TestTransaction
  public void testCreateStampsCreatedAtAndLeavesTheWarehouseActive() {
    // given
    var warehouse = new Warehouse();
    warehouse.businessUnitCode = "MWH.901";
    warehouse.location = "AMSTERDAM-002";
    warehouse.capacity = 20;
    warehouse.stock = 5;

    // when
    warehouseRepository.create(warehouse);
    forceDatabaseRoundTrip();

    // then
    var stored = warehouseRepository.findByBusinessUnitCode("MWH.901");
    assertNotNull(stored);
    assertNotNull(stored.createdAt, "create() must stamp createdAt");
    assertNull(stored.archivedAt);
    assertEquals("AMSTERDAM-002", stored.location);
    assertEquals(20, stored.capacity);
    assertEquals(5, stored.stock);
    assertTrue(businessUnitCodesOfAll().contains("MWH.901"));
  }

  /** C7 — remove() is a genuine hard delete; the archive flow just never calls it. */
  @Test
  @TestTransaction
  public void testRemoveHardDeletesTheRow() {
    // given
    var warehouse = new Warehouse();
    warehouse.businessUnitCode = "MWH.902";
    warehouse.location = "AMSTERDAM-002";
    warehouse.capacity = 20;
    warehouse.stock = 5;
    warehouseRepository.create(warehouse);
    forceDatabaseRoundTrip();

    // when
    warehouseRepository.remove(warehouse);
    forceDatabaseRoundTrip();

    // then
    assertEquals(0, warehouseRepository.find("businessUnitCode", "MWH.902").count());
  }

  private java.util.List<String> businessUnitCodesOfAll() {
    return warehouseRepository.getAll().stream().map(w -> w.businessUnitCode).toList();
  }

  /** Flushes pending changes and detaches everything, so the next query really hits the database. */
  private void forceDatabaseRoundTrip() {
    warehouseRepository.flush();
    warehouseRepository.getEntityManager().clear();
  }
}
