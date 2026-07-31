package com.fulfilment.application.monolith.warehouses.adapters.database;

import com.fulfilment.application.monolith.warehouses.domain.models.Warehouse;
import jakarta.persistence.Cacheable;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "warehouse")
@Cacheable
public class DbWarehouse {

  @Id @GeneratedValue public Long id;

  public String businessUnitCode;

  public String location;

  public Integer capacity;

  public Integer stock;

  public LocalDateTime createdAt;

  public LocalDateTime archivedAt;

  public DbWarehouse() {}

  public Warehouse toWarehouse() {
    var warehouse = new Warehouse();
    warehouse.businessUnitCode = this.businessUnitCode;
    warehouse.location = this.location;
    warehouse.capacity = this.capacity;
    warehouse.stock = this.stock;
    warehouse.createdAt = this.createdAt;
    warehouse.archivedAt = this.archivedAt;
    return warehouse;
  }

  /** Builds a new, unmanaged entity out of a domain warehouse. */
  public static DbWarehouse fromWarehouse(Warehouse warehouse) {
    var dbWarehouse = new DbWarehouse();
    dbWarehouse.mergeFrom(warehouse);
    return dbWarehouse;
  }

  /**
   * Copies the mutable state of a domain warehouse onto this entity. The primary key is untouched,
   * so this is safe to call on a managed row.
   */
  public void mergeFrom(Warehouse warehouse) {
    this.businessUnitCode = warehouse.businessUnitCode;
    this.location = warehouse.location;
    this.capacity = warehouse.capacity;
    this.stock = warehouse.stock;
    this.createdAt = warehouse.createdAt;
    this.archivedAt = warehouse.archivedAt;
  }
}
