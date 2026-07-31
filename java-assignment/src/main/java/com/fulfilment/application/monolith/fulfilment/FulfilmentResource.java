package com.fulfilment.application.monolith.fulfilment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.util.List;

/**
 * Manages which warehouses fulfil which products for which stores.
 *
 * <p>Hand-written rather than generated: {@code warehouse-openapi.yaml} covers the warehouse API
 * only, and extending a frozen spec was out of scope for this feature.
 *
 * <p>Request and response bodies are records rather than the {@link Fulfilment} entity, so the
 * schema can change without silently changing the published API. The surrounding hand-written
 * resources do expose their entities directly, but that is the flaw QUESTIONS.md answer 1 singles
 * out — following the convention here would have been consistent and wrong.
 *
 * <p>Error mapping is done with exception mappers typed to the concrete fulfilment exceptions, in
 * the same shape used by the warehouse adapter, rather than adding another
 * {@code ExceptionMapper<Exception>} catch-all — the codebase already carries two of those.
 */
@Path("fulfilment")
@ApplicationScoped
@Produces("application/json")
@Consumes("application/json")
public class FulfilmentResource {

  @Inject FulfilmentService fulfilmentService;

  @GET
  public List<FulfilmentResponse> list() {
    return fulfilmentService.listAll().stream().map(FulfilmentResponse::from).toList();
  }

  @GET
  @Path("store/{storeId}")
  public List<FulfilmentResponse> listForStore(Long storeId) {
    return fulfilmentService.listForStore(storeId).stream().map(FulfilmentResponse::from).toList();
  }

  @POST
  public Response associate(FulfilmentRequest request) {
    if (request == null) {
      throw new FulfilmentValidationException("A fulfilment association must be provided.");
    }

    Fulfilment created =
        fulfilmentService.associate(
            request.storeId(), request.productId(), request.warehouseBusinessUnitCode());

    return Response.ok(FulfilmentResponse.from(created)).status(201).build();
  }

  @DELETE
  @Path("{id}")
  public Response remove(Long id) {
    fulfilmentService.remove(id);
    return Response.status(204).build();
  }

  /** A cap breach or a bad reference surfaces as 400. */
  @Provider
  public static class ValidationExceptionMapper
      implements ExceptionMapper<FulfilmentValidationException> {

    @Inject ObjectMapper objectMapper;

    @Override
    public Response toResponse(FulfilmentValidationException exception) {
      return errorResponse(objectMapper, exception, 400);
    }
  }

  /** An unknown association surfaces as 404. */
  @Provider
  public static class NotFoundExceptionMapper
      implements ExceptionMapper<FulfilmentNotFoundException> {

    @Inject ObjectMapper objectMapper;

    @Override
    public Response toResponse(FulfilmentNotFoundException exception) {
      return errorResponse(objectMapper, exception, 404);
    }
  }

  private static Response errorResponse(
      ObjectMapper objectMapper, RuntimeException exception, int code) {
    ObjectNode exceptionJson = objectMapper.createObjectNode();
    exceptionJson.put("exceptionType", exception.getClass().getName());
    exceptionJson.put("code", code);

    if (exception.getMessage() != null) {
      exceptionJson.put("error", exception.getMessage());
    }

    return Response.status(code).entity(exceptionJson).build();
  }
}
