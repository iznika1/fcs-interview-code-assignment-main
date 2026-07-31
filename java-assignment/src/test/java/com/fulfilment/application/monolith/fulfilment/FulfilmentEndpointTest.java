package com.fulfilment.application.monolith.fulfilment;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

/**
 * HTTP-level contract for the fulfilment endpoints: status codes and error shape.
 *
 * <p>These go over the wire, so {@code @TestTransaction} does not apply and the rows really persist.
 * The test therefore works on store 3 with MWH.023 — a combination {@link FulfilmentServiceTest}
 * never touches — and deletes what it creates, so neither class can perturb the other regardless of
 * execution order.
 */
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class FulfilmentEndpointTest {

  private static final String PATH = "fulfilment";
  private static final long STORE_BESTA = 3L;
  private static final long PRODUCT_BESTA = 3L;
  private static final String WAREHOUSE = "MWH.023";

  private static Integer createdId;

  @Test
  @Order(1)
  public void createReturns201() {
    createdId =
        given()
            .contentType("application/json")
            .body(body(STORE_BESTA, PRODUCT_BESTA, WAREHOUSE))
            .when()
            .post(PATH)
            .then()
            .statusCode(201)
            .extract()
            .path("id");
  }

  @Test
  @Order(2)
  public void listIncludesTheAssociation() {
    given().when().get(PATH).then().statusCode(200).body(containsString(WAREHOUSE));
  }

  @Test
  @Order(3)
  public void listForStoreIncludesTheAssociation() {
    given()
        .when()
        .get(PATH + "/store/" + STORE_BESTA)
        .then()
        .statusCode(200)
        .body(containsString(WAREHOUSE));
  }

  @Test
  @Order(4)
  public void duplicateAssociationReturns400() {
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
  @Order(5)
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
  @Order(6)
  public void deleteReturns204() {
    given().when().delete(PATH + "/" + createdId).then().statusCode(204);
  }

  @Test
  @Order(7)
  public void deleteUnknownReturns404() {
    given().when().delete(PATH + "/9999").then().statusCode(404);
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
