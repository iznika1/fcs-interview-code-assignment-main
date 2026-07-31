package com.fulfilment.application.monolith.fulfilment;

import jakarta.persistence.Cacheable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Associates a {@code Warehouse} as a fulfilment unit for one {@code Product} in one {@code Store}.
 *
 * <p>The warehouse is referenced by its business unit code rather than a row id, matching how the
 * rest of the application identifies warehouses: a replacement archives one row and creates another
 * under the same code, so the code is the stable identity and a row id is not.
 *
 * <p>Store and product are referenced by id because that is their identity in this codebase.
 */
@Entity
@Table(
    name = "fulfilment",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_fulfilment_store_product_warehouse",
            columnNames = {"storeId", "productId", "warehouseBusinessUnitCode"}))
@Cacheable
public class Fulfilment {

  @Id @GeneratedValue public Long id;

  @Column(nullable = false)
  public Long storeId;

  @Column(nullable = false)
  public Long productId;

  @Column(nullable = false, length = 40)
  public String warehouseBusinessUnitCode;

  public Fulfilment() {}

  public Fulfilment(Long storeId, Long productId, String warehouseBusinessUnitCode) {
    this.storeId = storeId;
    this.productId = productId;
    this.warehouseBusinessUnitCode = warehouseBusinessUnitCode;
  }
}
