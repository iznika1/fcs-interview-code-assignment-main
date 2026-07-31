package com.fulfilment.application.monolith.stores;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Acceptance rows S1–S4: the legacy system is notified only once the transaction that changed a
 * store has committed, and it is notified with the persisted entity rather than the request body.
 *
 * <p>Rollbacks are provoked with a duplicate store name — {@code Store.name} is unique and {@code
 * TONSTAD} is seeded by {@code import.sql} — which fails the insert after the event was fired.
 */
@QuarkusTest
public class StoreLegacySyncTest {

  private static final String PATH = "store";

  @Inject RecordingLegacyStoreManagerGateway legacyGateway;

  @BeforeEach
  void resetRecorder() {
    legacyGateway.reset();
  }

  /** S1 — a successful create notifies the legacy gateway exactly once. */
  @Test
  void createNotifiesLegacyGatewayOnce() {
    Long id = createStore("S1-STORE", 7);

    assertEquals(1, legacyGateway.created().size(), "expected exactly one legacy create");
    assertTrue(legacyGateway.updated().isEmpty(), "create must not notify an update");

    Store notified = legacyGateway.created().get(0);
    assertNotNull(notified.id, "gateway must receive the persisted entity, which has an id");
    assertEquals(id, notified.id);
    assertEquals("S1-STORE", notified.name);
    assertEquals(7, notified.quantityProductsInStock);
  }

  /** S2 — a transaction that rolls back does not notify the legacy gateway. */
  @Test
  void rolledBackCreateDoesNotNotifyLegacyGateway() {
    // 'TONSTAD' is already seeded and Store.name is unique, so this insert fails and the
    // transaction rolls back after the event has been fired but before it is delivered.
    given()
        .contentType(ContentType.JSON)
        .body("{\"name\":\"TONSTAD\",\"quantityProductsInStock\":1}")
        .when()
        .post(PATH)
        .then()
        .statusCode(greaterThanOrEqualTo(400));

    assertTrue(
        legacyGateway.created().isEmpty(), "a rolled back create must not reach the legacy system");
    assertTrue(legacyGateway.updated().isEmpty());
  }

  /** S3 — update propagates only after commit. */
  @Test
  void updateNotifiesOnlyAfterCommit() {
    Long id = createStore("S3-STORE", 4);
    legacyGateway.reset();

    given()
        .contentType(ContentType.JSON)
        .body("{\"name\":\"S3-STORE-RENAMED\",\"quantityProductsInStock\":9}")
        .when()
        .put(PATH + "/" + id)
        .then()
        .statusCode(200);

    assertEquals(1, legacyGateway.updated().size(), "expected exactly one legacy update");
    assertEquals("S3-STORE-RENAMED", legacyGateway.updated().get(0).name);
    assertEquals(9, legacyGateway.updated().get(0).quantityProductsInStock);

    // Now make the same update fail: renaming to a seeded name breaks the unique constraint.
    legacyGateway.reset();
    given()
        .contentType(ContentType.JSON)
        .body("{\"name\":\"TONSTAD\",\"quantityProductsInStock\":9}")
        .when()
        .put(PATH + "/" + id)
        .then()
        .statusCode(greaterThanOrEqualTo(400));

    assertTrue(
        legacyGateway.updated().isEmpty(), "a rolled back update must not reach the legacy system");
    given()
        .when()
        .get(PATH + "/" + id)
        .then()
        .statusCode(200)
        .body("name", equalTo("S3-STORE-RENAMED"));
  }

  /** S3 — patch propagates only after commit, and is a real partial update. */
  @Test
  void patchAppliesOnlySuppliedFieldsAndNotifiesOnlyAfterCommit() {
    Long id = createStore("S3-PATCH-STORE", 4);
    legacyGateway.reset();

    // Only the stock is supplied; the name must survive.
    given()
        .contentType(ContentType.JSON)
        .body("{\"quantityProductsInStock\":11}")
        .when()
        .patch(PATH + "/" + id)
        .then()
        .statusCode(200)
        .body("name", equalTo("S3-PATCH-STORE"))
        .body("quantityProductsInStock", equalTo(11));

    assertEquals(1, legacyGateway.updated().size());
    Store notified = legacyGateway.updated().get(0);
    assertEquals(id, notified.id);
    assertEquals("S3-PATCH-STORE", notified.name);
    assertEquals(11, notified.quantityProductsInStock);

    // A patch that rolls back must not reach the legacy system either.
    legacyGateway.reset();
    given()
        .contentType(ContentType.JSON)
        .body("{\"name\":\"KALLAX\"}")
        .when()
        .patch(PATH + "/" + id)
        .then()
        .statusCode(greaterThanOrEqualTo(400));

    assertTrue(legacyGateway.updated().isEmpty());
  }

  /** S4 — the gateway receives the persisted entity, not the raw request body. */
  @Test
  void gatewayReceivesPersistedEntityNotRequestBody() {
    Long id = createStore("S4-STORE", 2);
    legacyGateway.reset();

    // The request body carries no id, so a gateway that received it would see id == null.
    given()
        .contentType(ContentType.JSON)
        .body("{\"name\":\"S4-STORE-UPDATED\",\"quantityProductsInStock\":6}")
        .when()
        .put(PATH + "/" + id)
        .then()
        .statusCode(200);

    Store notified = legacyGateway.updated().get(0);
    assertNotNull(notified.id, "the request body has no id; the persisted entity does");
    assertEquals(id, notified.id);
    assertEquals("S4-STORE-UPDATED", notified.name);
    assertEquals(6, notified.quantityProductsInStock);
  }

  private Long createStore(String name, int stock) {
    return given()
        .contentType(ContentType.JSON)
        .body("{\"name\":\"" + name + "\",\"quantityProductsInStock\":" + stock + "}")
        .when()
        .post(PATH)
        .then()
        .statusCode(201)
        .extract()
        .jsonPath()
        .getLong("id");
  }
}
