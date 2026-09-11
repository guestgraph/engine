package io.guestgraph.engine.api;

import io.guestgraph.service.ServiceException;
import org.springframework.http.HttpStatus;

public class ConflictException extends ServiceException {
  public ConflictException(String detail) {
    super(HttpStatus.CONFLICT, "conflict", "Conflict", detail);
  }
}
