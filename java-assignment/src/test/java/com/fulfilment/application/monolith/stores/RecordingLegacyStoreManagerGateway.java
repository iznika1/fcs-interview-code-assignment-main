package com.fulfilment.application.monolith.stores;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Test stand-in for {@link LegacyStoreManagerGateway}. The real gateway writes a temp file and then
 * deletes it again, which leaves nothing to assert on, so this records the calls instead.
 *
 * <p>Registered as a global CDI alternative, so every {@code @QuarkusTest} gets it in place of the
 * production bean. Production code is untouched.
 */
@Alternative
@Priority(1)
@ApplicationScoped
public class RecordingLegacyStoreManagerGateway extends LegacyStoreManagerGateway {

  private final List<Store> created = new CopyOnWriteArrayList<>();
  private final List<Store> updated = new CopyOnWriteArrayList<>();

  @Override
  public void createStoreOnLegacySystem(Store store) {
    created.add(store);
  }

  @Override
  public void updateStoreOnLegacySystem(Store store) {
    updated.add(store);
  }

  public List<Store> created() {
    return created;
  }

  public List<Store> updated() {
    return updated;
  }

  public void reset() {
    created.clear();
    updated.clear();
  }
}
