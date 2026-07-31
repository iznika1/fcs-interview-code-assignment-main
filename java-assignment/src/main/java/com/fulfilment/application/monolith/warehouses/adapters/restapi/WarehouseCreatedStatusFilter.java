package com.fulfilment.application.monolith.warehouses.adapters.restapi;

import jakarta.ws.rs.HttpMethod;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

/**
 * Answers {@code POST /warehouse} with 201, as warehouse-openapi.yaml declares.
 *
 * <p>The generator turns that operation into a method returning the bean itself rather than a {@code
 * Response}, and the JAX-RS metadata lives on the generated interface — so neither a {@code
 * Response.status(201)} nor a {@code @ResponseStatus(201)} on the implementation is reachable, and
 * the framework defaults to 200. Rewriting the status here keeps the contract honest without
 * hand-editing generated code. Scoped as tightly as possible: exact path, POST only, and only when
 * the response would otherwise be a plain 200.
 */
@Provider
public class WarehouseCreatedStatusFilter implements ContainerResponseFilter {

  private static final String WAREHOUSE_COLLECTION_PATH = "warehouse";

  @Override
  public void filter(ContainerRequestContext request, ContainerResponseContext response) {
    if (!HttpMethod.POST.equals(request.getMethod())) {
      return;
    }
    if (response.getStatus() != Response.Status.OK.getStatusCode()) {
      return;
    }

    var path = request.getUriInfo().getPath();
    if (path.startsWith("/")) {
      path = path.substring(1);
    }

    if (WAREHOUSE_COLLECTION_PATH.equals(path)) {
      response.setStatus(Response.Status.CREATED.getStatusCode());
    }
  }
}
