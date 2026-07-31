package com.fulfilment.application.monolith.warehouses.domain.usecases;

import com.fulfilment.application.monolith.warehouses.domain.models.Location;
import com.fulfilment.application.monolith.warehouses.domain.ports.LocationResolver;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * In-memory {@link LocationResolver} carrying the same table as the production {@code
 * LocationGateway}, so the fixtures used here mean the same thing end to end.
 *
 * <p>Returns {@code null} for an unknown identifier, as the port specifies.
 */
class FakeLocationResolver implements LocationResolver {

  private final Map<String, Location> locations = new LinkedHashMap<>();

  FakeLocationResolver() {
    add(new Location("ZWOLLE-001", 1, 40));
    add(new Location("ZWOLLE-002", 2, 50));
    add(new Location("AMSTERDAM-001", 5, 100));
    add(new Location("AMSTERDAM-002", 3, 75));
    add(new Location("TILBURG-001", 1, 40));
    add(new Location("HELMOND-001", 1, 45));
    add(new Location("EINDHOVEN-001", 2, 70));
    add(new Location("VETSBY-001", 1, 90));
  }

  private void add(Location location) {
    locations.put(location.identification, location);
  }

  @Override
  public Location resolveByIdentifier(String identifier) {
    return locations.get(identifier);
  }
}
