package io.guestgraph.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * What nobody foresaw is still a problem detail: any exception no handler took is logged in full on
 * the server and answered as the family's internal-error problem, whose detail says nothing of the
 * cause. Last in order, so the framework's own handlers and a ServiceException's answer come first.
 * Vendored from guestgraph/service-conventions, never edited in a service.
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class ServiceExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(ServiceExceptionHandler.class);

  @ExceptionHandler(Exception.class)
  public ProblemDetail unforeseen(Exception e) {
    log.error("Unhandled exception while serving a request", e);
    return Problems.of(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "internal-error",
        "Internal server error",
        "An unexpected error occurred");
  }
}
