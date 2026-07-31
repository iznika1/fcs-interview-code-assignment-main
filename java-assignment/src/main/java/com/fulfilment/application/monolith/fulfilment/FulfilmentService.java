package com.fulfilment.application.monolith.fulfilment;

import com.fulfilment.application.monolith.products.Product;
import com.fulfilment.application.monolith.products.ProductRepository;
import com.fulfilment.application.monolith.stores.Store;
import com.fulfilment.application.monolith.warehouses.domain.ports.WarehouseStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Set;

/**
 * Associates warehouses as fulfilment units for products in stores, enforcing the three caps from
 * the assignment:
 *
 * <ol>
 *   <li>a product may be fulfilled by at most 2 warehouses per store;
 *   <li>a store may be fulfilled by at most 3 warehouses in total;
 *   <li>a warehouse may store at most 5 product types.
 * </ol>
 *
 * <p>Each cap counts <em>distinct</em> participants, and an association that reuses a participant
 * the store or warehouse already has does not consume a new slot. That is what makes the second and
 * third rules workable at all: adding a fourth product to a store's existing warehouse must not
 * trip the three-warehouse cap.
 *
 * <p>Warehouses are resolved through the {@link WarehouseStore} port, which returns active rows
 * only, so an archived warehouse cannot be assigned as a fulfilment unit.
 */
@ApplicationScoped
public class FulfilmentService {

  static final int MAX_WAREHOUSES_PER_PRODUCT_PER_STORE = 2;
  static final int MAX_WAREHOUSES_PER_STORE = 3;
  static final int MAX_PRODUCT_TYPES_PER_WAREHOUSE = 5;

  @Inject FulfilmentRepository fulfilmentRepository;
  @Inject ProductRepository productRepository;
  @Inject WarehouseStore warehouseStore;

  public List<Fulfilment> listAll() {
    return fulfilmentRepository.listAll();
  }

  public List<Fulfilment> listForStore(Long storeId) {
    return fulfilmentRepository.find("storeId", storeId).list();
  }

  /**
   * Records that {@code warehouseBusinessUnitCode} fulfils {@code productId} for {@code storeId}.
   *
   * @throws FulfilmentValidationException if a referenced entity does not exist, the association
   *     already exists, or any of the three caps would be exceeded
   */
  @Transactional
  public Fulfilment associate(Long storeId, Long productId, String warehouseBusinessUnitCode) {
    validateReferencesExist(storeId, productId, warehouseBusinessUnitCode);
    validateNotAlreadyAssociated(storeId, productId, warehouseBusinessUnitCode);
    validateWithinCaps(storeId, productId, warehouseBusinessUnitCode);

    var fulfilment = new Fulfilment(storeId, productId, warehouseBusinessUnitCode);
    fulfilmentRepository.persist(fulfilment);
    return fulfilment;
  }

  @Transactional
  public void remove(Long id) {
    Fulfilment fulfilment = fulfilmentRepository.findById(id);
    if (fulfilment == null) {
      throw new FulfilmentNotFoundException(id);
    }
    fulfilmentRepository.delete(fulfilment);
  }

  private void validateReferencesExist(
      Long storeId, Long productId, String warehouseBusinessUnitCode) {
    if (storeId == null || productId == null || isBlank(warehouseBusinessUnitCode)) {
      throw new FulfilmentValidationException(
          "storeId, productId and warehouseBusinessUnitCode are all required.");
    }
    if (Store.<Store>findById(storeId) == null) {
      throw new FulfilmentValidationException("Store with id of " + storeId + " does not exist.");
    }
    Product product = productRepository.findById(productId);
    if (product == null) {
      throw new FulfilmentValidationException(
          "Product with id of " + productId + " does not exist.");
    }
    if (warehouseStore.findByBusinessUnitCode(warehouseBusinessUnitCode) == null) {
      throw new FulfilmentValidationException(
          "No active warehouse with business unit code '"
              + warehouseBusinessUnitCode
              + "' exists.");
    }
  }

  private void validateNotAlreadyAssociated(
      Long storeId, Long productId, String warehouseBusinessUnitCode) {
    if (fulfilmentRepository.findAssociation(storeId, productId, warehouseBusinessUnitCode)
        != null) {
      throw new FulfilmentValidationException(
          "Warehouse '"
              + warehouseBusinessUnitCode
              + "' already fulfils product "
              + productId
              + " for store "
              + storeId
              + ".");
    }
  }

  private void validateWithinCaps(
      Long storeId, Long productId, String warehouseBusinessUnitCode) {
    Set<String> warehousesForPair = fulfilmentRepository.warehousesFor(storeId, productId);
    if (!warehousesForPair.contains(warehouseBusinessUnitCode)
        && warehousesForPair.size() >= MAX_WAREHOUSES_PER_PRODUCT_PER_STORE) {
      throw new FulfilmentValidationException(
          "Product "
              + productId
              + " is already fulfilled by the maximum of "
              + MAX_WAREHOUSES_PER_PRODUCT_PER_STORE
              + " warehouses for store "
              + storeId
              + ".");
    }

    Set<String> warehousesForStore = fulfilmentRepository.warehousesForStore(storeId);
    if (!warehousesForStore.contains(warehouseBusinessUnitCode)
        && warehousesForStore.size() >= MAX_WAREHOUSES_PER_STORE) {
      throw new FulfilmentValidationException(
          "Store "
              + storeId
              + " is already fulfilled by the maximum of "
              + MAX_WAREHOUSES_PER_STORE
              + " warehouses.");
    }

    Set<Long> productsForWarehouse =
        fulfilmentRepository.productsForWarehouse(warehouseBusinessUnitCode);
    if (!productsForWarehouse.contains(productId)
        && productsForWarehouse.size() >= MAX_PRODUCT_TYPES_PER_WAREHOUSE) {
      throw new FulfilmentValidationException(
          "Warehouse '"
              + warehouseBusinessUnitCode
              + "' already stores the maximum of "
              + MAX_PRODUCT_TYPES_PER_WAREHOUSE
              + " product types.");
    }
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
