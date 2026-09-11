package io.guestgraph.engine.resolution;

import io.guestgraph.service.ServiceException;
import java.util.UUID;
import org.springframework.http.HttpStatus;

public class ReviewNotFoundException extends ServiceException {

  public ReviewNotFoundException(UUID reviewId) {
    super(
        HttpStatus.NOT_FOUND,
        "not-found",
        "Resource not found",
        "No match review " + reviewId + " in this tenant");
  }
}
