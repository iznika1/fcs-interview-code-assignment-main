package com.fulfilment.application.monolith.warehouses.adapters.restapi;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.containsString;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

/**
 * Acceptance rows W1 and W7–W10 for the warehouse REST adapter.
 *
 * <p>This duplicates part of {@link WarehouseEndpointIT} on purpose: the Failsafe plugin is declared
 * only in the {@code native} profile, so the {@code *IT} class never runs in a normal build. These
 * cases execute under Surefire.
 *
 * <p>Seeded rows are only read, never mutated — the archive cases create their own warehouse first
 * so the test stays order-independent alongside the other {@code @QuarkusTest} classes.
 */
@QuarkusTest
public class WarehouseEndpointTest {

  private static final String PATH = "warehouse";

  /** W1 — a valid create returns 201 with the stored representation. */
  @Test
  public void testCreateValidWarehouseReturns201() {
    given()
        .contentType("application/json")
        .body(
            """
            {"businessUnitCode":"MWH.101","location":"AMSTERDAM-002","capacity":20,"stock":5}\
            """)
        .when()
        .post(PATH)
        .then()
        .statusCode(201)
        .body("businessUnitCode", equalTo("MWH.101"))
        .body("location", equalTo("AMSTERDAM-002"))
        .body("capacity", equalTo(20))
        .body("stock", equalTo(5));
  }

  /** W7 — getting an existing warehouse returns 200 with every field populated. */
  @Test
  public void testGetExistingWarehouseReturns200WithAllFields() {
    given()
        .when()
        .get(PATH + "/MWH.012")
        .then()
        .statusCode(200)
        .body("businessUnitCode", equalTo("MWH.012"))
        .body("location", equalTo("AMSTERDAM-001"))
        .body("capacity", equalTo(50))
        .body("stock", equalTo(5));
  }

  /** W8 — an unknown business unit code returns 404. */
  @Test
  public void testGetUnknownWarehouseReturns404() {
    given().when().get(PATH + "/MWH.999").then().statusCode(404);
  }

  /** W9 — archiving an existing warehouse returns 204 and removes it from the active list. */
  @Test
  public void testArchiveExistingWarehouseReturns204() {
    given()
        .contentType("application/json")
        .body(
            """
            {"businessUnitCode":"MWH.102","location":"HELMOND-001","capacity":20,"stock":5}\
            """)
        .when()
        .post(PATH)
        .then()
        .statusCode(201);

    given().when().delete(PATH + "/MWH.102").then().statusCode(204);

    // Archiving is a soft delete: the row survives, but it is no longer active.
    given().when().get(PATH + "/MWH.102").then().statusCode(404);
    given().when().get(PATH).then().statusCode(200).body(not(containsString("MWH.102")));
  }

  /** W10 — archiving an unknown business unit code returns 404. */
  @Test
  public void testArchiveUnknownWarehouseReturns404() {
    given().when().delete(PATH + "/MWH.999").then().statusCode(404);
  }
}
