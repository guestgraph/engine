package io.guestgraph.engine.api;

public class ConflictException extends RuntimeException {

  public ConflictException(String detail) {
    super(detail);
  }
}
