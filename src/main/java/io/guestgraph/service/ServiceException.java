package io.guestgraph.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

/**
 * The exception every service-specific exception extends: it carries its answer, the status, the
 * family's type, the title and the detail, so Spring's problem-details handler answers it and no
 * handler is written for it. A subclass adds named members through {@link #withProperty}. Vendored
 * from guestgraph/service-conventions, never edited in a service.
 */
public class ServiceException extends ErrorResponseException {

  public ServiceException(HttpStatus status, String slug, String title, String detail) {
    super(status, Problems.of(status, slug, title, detail), null);
  }

  public ServiceException(
      HttpStatus status, String slug, String title, String detail, Throwable cause) {
    super(status, Problems.of(status, slug, title, detail), cause);
  }

  /** A named member of the problem, never under a name RFC 9457 owns. */
  public final ServiceException withProperty(String name, Object value) {
    getBody().setProperty(name, value);
    return this;
  }

  @Override
  public String getMessage() {
    return getBody().getDetail() == null ? super.getMessage() : getBody().getDetail();
  }
}
