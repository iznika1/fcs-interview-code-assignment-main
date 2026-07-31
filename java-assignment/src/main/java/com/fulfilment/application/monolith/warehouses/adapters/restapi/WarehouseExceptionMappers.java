package com.fulfilment.application.monolith.warehouses.adapters.restapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fulfilment.application.monolith.warehouses.domain.exceptions.WarehouseNotFoundException;
import com.fulfilment.application.monolith.warehouses.domain.exceptions.WarehouseValidationException;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

/**
 * Translates the warehouse domain exceptions into the HTTP statuses declared by
 * warehouse-openapi.yaml. This lives in the adapter layer on purpose: the domain must not know about
 * JAX-RS.
 *
 * <p>These mappers are typed to a concrete exception, so they take precedence over the
 * {@code ExceptionMapper<Exception>} catch-all providers declared by the other resources. The
 * payload shape is kept identical to those so error responses stay consistent across the app.
 */
public final class WarehouseExceptionMappers {

  private static final Logger LOGGER = Logger.getLogger(WarehouseExceptionMappers.class.getName());

  private WarehouseExceptionMappers() {}

  /** Every business-rule violation surfaces as 400. */
  @Provider
  public static class ValidationExceptionMapper
      implements ExceptionMapper<WarehouseValidationException> {

    @Inject ObjectMapper objectMapper;

    @Override
    public Response toResponse(WarehouseValidationException exception) {
      return errorResponse(objectMapper, exception, 400);
    }
  }

  /** An unknown business unit code surfaces as 404. */
  @Provider
  public static class NotFoundExceptionMapper implements ExceptionMapper<WarehouseNotFoundException> {

    @Inject ObjectMapper objectMapper;

    @Override
    public Response toResponse(WarehouseNotFoundException exception) {
      return errorResponse(objectMapper, exception, 404);
    }
  }

  private static Response errorResponse(
      ObjectMapper objectMapper, RuntimeException exception, int code) {
    LOGGER.debugf(exception, "Mapping %s to HTTP %d", exception.getClass().getSimpleName(), code);

    ObjectNode exceptionJson = objectMapper.createObjectNode();
    exceptionJson.put("exceptionType", exception.getClass().getName());
    exceptionJson.put("code", code);

    if (exception.getMessage() != null) {
      exceptionJson.put("error", exception.getMessage());
    }

    return Response.status(code).entity(exceptionJson).build();
  }
}
