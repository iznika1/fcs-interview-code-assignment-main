package com.fulfilment.application.monolith.fulfilment;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;

import io.quarkus.test.junit.QuarkusTest;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * HTTP-level contract for the fulfilment endpoints: status codes and error shape.
 *
 * <p>These go over the wire, so {@code @TestTransaction} does not apply and rows really persist.
 * Isolation is handled two ways instead. Each test creates whatever it needs and every test is
 * independently runnable, so {@code -Dtest=FulfilmentEndpointTest#someMethod} works — sharing state
 * across an {@code @Order} chain would reproduce exactly the order-dependence QUESTIONS.md answer 3
 * criticises in {@code ProductEndpointTest}. And {@link #cleanUp()} removes every association for
 * the fixture store afterwards.
 *
 * <p>The fixture is store 3 with {@code MWH.023} and product 3 — a combination {@link
 * FulfilmentServiceTest} never touches, and product 3 specifically because {@code
 * ProductEndpointTest} deletes product 1.
 */
@QuarkusTest
public class FulfilmentEndpointTest {

  private static final String PATH = "fulfilment";
  private static final long STORE_BESTA = 3L;
  private static final long PRODUCT_BESTA = 3L;
  private static final String WAREHOUSE = "MWH.023";

  @AfterEach
  public void cleanUp() {
    List<Integer> ids =
        given()
            .when()
            .get(PATH + "/store/" + STORE_BESTA)
            .then()
            .statusCode(200)
            .extract()
            .path("id");

    ids.forEach(id -> given().when().delete(PATH + "/" + id).then().statusCode(204));
  }

  @Test
  public void createReturns201() {
    given()
        .contentType("application/json")
        .body(body(STORE_BESTA, PRODUCT_BESTA, WAREHOUSE))
        .when()
        .post(PATH)
        .then()
        .statusCode(201)
        .body("storeId", org.hamcrest.CoreMatchers.is((int) STORE_BESTA))
        .body("warehouseBusinessUnitCode", org.hamcrest.CoreMatchers.is(WAREHOUSE));
  }

  @Test
  public void listIncludesTheAssociation() {
    givenAnAssociation();

    given().when().get(PATH).then().statusCode(200).body(containsString(WAREHOUSE));
  }

  @Test
  public void listForStoreIncludesTheAssociation() {
    givenAnAssociation();

    given()
        .when()
        .get(PATH + "/store/" + STORE_BESTA)
        .then()
        .statusCode(200)
        .body(containsString(WAREHOUSE));
  }

  @Test
  public void duplicateAssociationReturns400() {
    givenAnAssociation();

    given()
        .contentType("application/json")
        .body(body(STORE_BESTA, PRODUCT_BESTA, WAREHOUSE))
        .when()
        .post(PATH)
        .then()
        .statusCode(400)
        .body(containsString("already fulfils"));
  }

  @Test
  public void unknownWarehouseReturns400() {
    given()
        .contentType("application/json")
        .body(body(STORE_BESTA, PRODUCT_BESTA, "MWH.999"))
        .when()
        .post(PATH)
        .then()
        .statusCode(400)
        .body(containsString("No active warehouse"));
  }

  @Test
  public void deleteReturns204() {
    int id = givenAnAssociation();

    given().when().delete(PATH + "/" + id).then().statusCode(204);
  }

  @Test
  public void deleteUnknownReturns404() {
    given().when().delete(PATH + "/9999").then().statusCode(404);
  }

  /** Creates the fixture association over HTTP and returns its id. */
  private static int givenAnAssociation() {
    return given()
        .contentType("application/json")
        .body(body(STORE_BESTA, PRODUCT_BESTA, WAREHOUSE))
        .when()
        .post(PATH)
        .then()
        .statusCode(201)
        .extract()
        .path("id");
  }

  private static String body(long storeId, long productId, String warehouseBusinessUnitCode) {
    return "{\"storeId\":"
        + storeId
        + ",\"productId\":"
        + productId
        + ",\"warehouseBusinessUnitCode\":\""
        + warehouseBusinessUnitCode
        + "\"}";
  }
}
