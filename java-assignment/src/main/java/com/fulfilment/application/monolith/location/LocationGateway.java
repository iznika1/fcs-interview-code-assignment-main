package com.fulfilment.application.monolith.location;

import com.fulfilment.application.monolith.warehouses.domain.models.Location;
import com.fulfilment.application.monolith.warehouses.domain.ports.LocationResolver;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class LocationGateway implements LocationResolver {

  private static final List<Location> locations = new ArrayList<>();

  static {
    locations.add(new Location("ZWOLLE-001", 1, 40));
    locations.add(new Location("ZWOLLE-002", 2, 50));
    locations.add(new Location("AMSTERDAM-001", 5, 100));
    locations.add(new Location("AMSTERDAM-002", 3, 75));
    locations.add(new Location("TILBURG-001", 1, 40));
    locations.add(new Location("HELMOND-001", 1, 45));
    locations.add(new Location("EINDHOVEN-001", 2, 70));
    locations.add(new Location("VETSBY-001", 1, 90));
  }

  /**
   * Resolves a location by its identification.
   *
   * @return the matching {@link Location}, or {@code null} when the identifier is unknown. Callers
   *     decide what an unknown location means — the use cases translate it into a validation error.
   */
  @Override
  public Location resolveByIdentifier(String identifier) {
    if (identifier == null) {
      return null;
    }
    return locations.stream()
        .filter(location -> identifier.equals(location.identification))
        .findFirst()
        .orElse(null);
  }
}
