package io.guestgraph.engine.api;

import io.guestgraph.service.ServiceException;
import org.springframework.http.HttpStatus;

/** Resource absent in the caller's tenant — cross-tenant ids intentionally look identical. */
public class NotFoundException extends ServiceException {
  public NotFoundException(String detail) {
    super(HttpStatus.NOT_FOUND, "not-found", "Resource not found", detail);
  }
}
