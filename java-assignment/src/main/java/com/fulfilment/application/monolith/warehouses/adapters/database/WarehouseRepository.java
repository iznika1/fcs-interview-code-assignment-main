package com.fulfilment.application.monolith.warehouses.adapters.database;

import com.fulfilment.application.monolith.warehouses.domain.exceptions.WarehouseNotFoundException;
import com.fulfilment.application.monolith.warehouses.domain.models.Warehouse;
import com.fulfilment.application.monolith.warehouses.domain.ports.WarehouseStore;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.List;

@ApplicationScoped
public class WarehouseRepository implements WarehouseStore, PanacheRepository<DbWarehouse> {

  private static final String ACTIVE_BY_BUSINESS_UNIT_CODE =
      "businessUnitCode = ?1 and archivedAt is null";

  /**
   * Only active warehouses. Archiving is a soft delete, so archived rows stay in the table to
   * preserve the history of a business unit code and must not show up here.
   */
  @Override
  public List<Warehouse> getAll() {
    return this.find("archivedAt is null").stream().map(DbWarehouse::toWarehouse).toList();
  }

  @Override
  @Transactional
  public void create(Warehouse warehouse) {
    var dbWarehouse = DbWarehouse.fromWarehouse(warehouse);
    if (dbWarehouse.createdAt == null) {
      dbWarehouse.createdAt = LocalDateTime.now();
      warehouse.createdAt = dbWarehouse.createdAt;
    }
    this.persist(dbWarehouse);
  }

  /**
   * Persists the mutable state of an existing warehouse, {@code archivedAt} included — that is what
   * makes the archive flow a soft delete rather than a row removal.
   */
  @Override
  @Transactional
  public void update(Warehouse warehouse) {
    var dbWarehouse = findActive(warehouse.businessUnitCode);
    if (dbWarehouse == null) {
      throw new WarehouseNotFoundException(warehouse.businessUnitCode);
    }
    dbWarehouse.mergeFrom(warehouse);
    this.persist(dbWarehouse);
  }

  /**
   * Hard delete of the active row for a business unit code. Provided for completeness of the port;
   * the archive flow deliberately does not use it, because dropping the row would destroy exactly
   * the history the "replace" feature exists to keep.
   */
  @Override
  @Transactional
  public void remove(Warehouse warehouse) {
    this.delete(ACTIVE_BY_BUSINESS_UNIT_CODE, warehouse.businessUnitCode);
  }

  /**
   * @return the active warehouse for this business unit code, or {@code null} when there is none.
   *     Archived rows share the code with their replacement, so the lookup has to filter them out.
   */
  @Override
  public Warehouse findByBusinessUnitCode(String buCode) {
    var dbWarehouse = findActive(buCode);
    return dbWarehouse == null ? null : dbWarehouse.toWarehouse();
  }

  private DbWarehouse findActive(String buCode) {
    if (buCode == null) {
      return null;
    }
    return this.find(ACTIVE_BY_BUSINESS_UNIT_CODE, buCode).firstResult();
  }
}
