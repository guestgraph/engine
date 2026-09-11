package io.guestgraph.engine.api;

import io.guestgraph.service.ServiceException;
import org.springframework.http.HttpStatus;

public class BadRequestException extends ServiceException {
  public BadRequestException(String detail) {
    super(HttpStatus.BAD_REQUEST, "invalid-request", "Invalid request", detail);
  }
}
