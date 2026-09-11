package io.guestgraph.engine.api;

import io.guestgraph.service.ServiceException;
import org.springframework.http.HttpStatus;

/**
 * A request tried to record an actor type its credential does not grant (FR-014). A malformed
 * claim, not an authorization failure — hence 400 rather than 403.
 */
public class InvalidActorClaimException extends ServiceException {
  public InvalidActorClaimException(String detail) {
    super(HttpStatus.BAD_REQUEST, "invalid-actor-claim", "Invalid actor claim", detail);
  }
}
