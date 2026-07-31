package com.fulfilment.application.monolith.warehouses.adapters.restapi;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;

import com.fulfilment.application.monolith.warehouses.domain.exceptions.WarehouseNotFoundException;
import com.fulfilment.application.monolith.warehouses.domain.exceptions.WarehouseValidationException;
import com.fulfilment.application.monolith.warehouses.domain.models.Warehouse;
import com.fulfilment.application.monolith.warehouses.domain.ports.ArchiveWarehouseOperation;
import com.fulfilment.application.monolith.warehouses.domain.ports.CreateWarehouseOperation;
import com.fulfilment.application.monolith.warehouses.domain.ports.ReplaceWarehouseOperation;
import com.fulfilment.application.monolith.warehouses.domain.ports.WarehouseStore;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Exercises the REST adapter in isolation, with the inbound ports replaced by in-memory fakes.
 *
 * <p>The point is to pin down what this layer alone is responsible for — status codes, path-to-port
 * translation and domain-exception mapping — without depending on the persistence adapter or the use
 * cases. {@link WarehouseEndpointTest} covers the same rows against the real wiring.
 */
@QuarkusTest
@TestProfile(WarehouseResourceImplTest.FakePortsProfile.class)
public class WarehouseResourceImplTest {

  private static final String PATH = "warehouse";

  @Test
  public void testCreateReturns201() {
    given()
        .contentType("application/json")
        .body(
            """
            {"businessUnitCode":"FAKE.002","location":"AMSTERDAM-002","capacity":20,"stock":5}\
            """)
        .when()
        .post(PATH)
        .then()
        .statusCode(201)
        .body("businessUnitCode", equalTo("FAKE.002"))
        .body("location", equalTo("AMSTERDAM-002"))
        .body("capacity", equalTo(20))
        .body("stock", equalTo(5));
  }

  @Test
  public void testValidationExceptionIsMappedTo400() {
    given()
        .contentType("application/json")
        .body(
            """
            {"businessUnitCode":"FAKE.002","location":"ATLANTIS-001","capacity":20,"stock":5}\
            """)
        .when()
        .post(PATH)
        .then()
        .statusCode(400)
        .body("code", equalTo(400))
        .body("error", containsString("ATLANTIS-001"))
        .body(
            "exceptionType",
            equalTo(
                "com.fulfilment.application.monolith.warehouses.domain.exceptions.WarehouseValidationException"));
  }

  @Test
  public void testGetExistingReturns200() {
    given()
        .when()
        .get(PATH + "/FAKE.001")
        .then()
        .statusCode(200)
        .body("businessUnitCode", equalTo("FAKE.001"))
        .body("location", equalTo("AMSTERDAM-001"))
        .body("capacity", equalTo(50))
        .body("stock", equalTo(5));
  }

  @Test
  public void testGetUnknownIsMappedTo404() {
    given()
        .when()
        .get(PATH + "/FAKE.999")
        .then()
        .statusCode(404)
        .body("code", equalTo(404))
        .body("error", containsString("FAKE.999"))
        .body(
            "exceptionType",
            equalTo(
                "com.fulfilment.application.monolith.warehouses.domain.exceptions.WarehouseNotFoundException"));
  }

  @Test
  public void testArchiveExistingReturns204WithNoBody() {
    given().when().delete(PATH + "/FAKE.001").then().statusCode(204).body(equalTo(""));
  }

  @Test
  public void testArchiveUnknownIsMappedTo404() {
    given().when().delete(PATH + "/FAKE.999").then().statusCode(404);
  }

  /** The path parameter wins over whatever business unit code the body happens to carry. */
  @Test
  public void testReplaceUsesThePathBusinessUnitCode() {
    given()
        .contentType("application/json")
        .body(
            """
            {"businessUnitCode":"IGNORED","location":"AMSTERDAM-001","capacity":50,"stock":5}\
            """)
        .when()
        .post(PATH + "/FAKE.001/replacement")
        .then()
        .statusCode(200)
        .body("businessUnitCode", equalTo("FAKE.001"));
  }

  @Test
  public void testListReturnsOnlyWhatTheStoreReturns() {
    given()
        .when()
        .get(PATH)
        .then()
        .statusCode(200)
        .body("businessUnitCode", equalTo(List.of("FAKE.001")));
  }

  public static class FakePortsProfile implements QuarkusTestProfile {
    @Override
    public Set<Class<?>> getEnabledAlternatives() {
      return Set.of(
          FakeWarehouseStore.class,
          FakeCreateOperation.class,
          FakeReplaceOperation.class,
          FakeArchiveOperation.class);
    }
  }

  /**
   * Request-scoped so every HTTP call starts from the same seeded state and the cases stay
   * order-independent without a reset hook on the test class.
   */
  @Alternative
  @RequestScoped
  public static class FakeWarehouseStore implements WarehouseStore {

    private final Map<String, Warehouse> active = new LinkedHashMap<>();

    @PostConstruct
    void seed() {
      active.put("FAKE.001", warehouse("FAKE.001", "AMSTERDAM-001", 50, 5));
    }

    @Override
    public List<Warehouse> getAll() {
      return new ArrayList<>(active.values());
    }

    @Override
    public void create(Warehouse warehouse) {
      active.put(warehouse.businessUnitCode, warehouse);
    }

    @Override
    public void update(Warehouse warehouse) {
      if (warehouse.archivedAt != null) {
        active.remove(warehouse.businessUnitCode);
      } else {
        active.put(warehouse.businessUnitCode, warehouse);
      }
    }

    @Override
    public void remove(Warehouse warehouse) {
      active.remove(warehouse.businessUnitCode);
    }

    @Override
    public Warehouse findByBusinessUnitCode(String buCode) {
      return active.get(buCode);
    }

    private static Warehouse warehouse(
        String businessUnitCode, String location, int capacity, int stock) {
      var warehouse = new Warehouse();
      warehouse.businessUnitCode = businessUnitCode;
      warehouse.location = location;
      warehouse.capacity = capacity;
      warehouse.stock = stock;
      warehouse.createdAt = LocalDateTime.now();
      return warehouse;
    }
  }

  @Alternative
  @ApplicationScoped
  public static class FakeCreateOperation implements CreateWarehouseOperation {

    @Inject FakeWarehouseStore store;

    @Override
    public void create(Warehouse warehouse) {
      if ("ATLANTIS-001".equals(warehouse.location)) {
        throw new WarehouseValidationException(
            "Location " + warehouse.location + " does not exist.");
      }
      store.create(warehouse);
    }
  }

  @Alternative
  @ApplicationScoped
  public static class FakeReplaceOperation implements ReplaceWarehouseOperation {

    @Inject FakeWarehouseStore store;

    @Override
    public void replace(Warehouse warehouse) {
      if (store.findByBusinessUnitCode(warehouse.businessUnitCode) == null) {
        throw new WarehouseNotFoundException(warehouse.businessUnitCode);
      }
      store.create(warehouse);
    }
  }

  @Alternative
  @ApplicationScoped
  public static class FakeArchiveOperation implements ArchiveWarehouseOperation {

    @Inject FakeWarehouseStore store;

    @Override
    public void archive(Warehouse warehouse) {
      warehouse.archivedAt = LocalDateTime.now();
      store.update(warehouse);
    }
  }
}
