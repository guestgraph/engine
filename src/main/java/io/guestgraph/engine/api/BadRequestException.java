package io.guestgraph.engine.api;

public class BadRequestException extends RuntimeException {

  public BadRequestException(String detail) {
    super(detail);
  }
}
