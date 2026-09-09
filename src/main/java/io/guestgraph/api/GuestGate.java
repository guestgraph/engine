package io.guestgraph.api;

import io.guestgraph.persistence.GuestQueryService;
import io.guestgraph.resolution.GraphMutationService;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * The one answer to "may this request proceed under this guest id": yes for an active guest, {@link
 * RetiredGuestException} for a retired one, {@link NotFoundException} for an id that never existed
 * in the tenant. Existence is checked first, so the hot paths pay nothing new; the walk over the
 * merge events runs only for an id that no guest carries (research R6).
 */
@Component
public class GuestGate {

  private final GuestQueryService guests;
  private final GraphMutationService graph;

  public GuestGate(GuestQueryService guests, GraphMutationService graph) {
    this.guests = guests;
    this.graph = graph;
  }

  public void require(UUID tenantId, UUID guestId) {
    if (guests.guestExists(tenantId, guestId)) {
      return;
    }
    throw graph
        .resolve(tenantId, guestId)
        .<RuntimeException>map(RetiredGuestException::new)
        .orElseGet(() -> new NotFoundException("No guest " + guestId + " in this tenant"));
  }
}
