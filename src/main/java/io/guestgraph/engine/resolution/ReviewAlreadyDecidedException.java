package io.guestgraph.engine.resolution;

import io.guestgraph.engine.domain.ReviewStatus;
import io.guestgraph.service.ServiceException;
import java.util.UUID;
import org.springframework.http.HttpStatus;

/** A review is decided exactly once; the first decision stands (FR-018). */
public class ReviewAlreadyDecidedException extends ServiceException {

  public ReviewAlreadyDecidedException(UUID reviewId, ReviewStatus status) {
    super(
        HttpStatus.CONFLICT,
        "review-already-decided",
        "Conflict",
        "Match review " + reviewId + " was already decided (" + status + ")");
  }
}
