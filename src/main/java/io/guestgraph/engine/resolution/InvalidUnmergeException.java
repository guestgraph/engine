package io.guestgraph.engine.resolution;

import io.guestgraph.service.ServiceException;
import org.springframework.http.HttpStatus;

/** The requested unmerge is not executable (single-record guest, unlinked record, ...). */
public class InvalidUnmergeException extends ServiceException {

  public InvalidUnmergeException(String message) {
    super(HttpStatus.BAD_REQUEST, "invalid-unmerge", "Invalid unmerge", message);
  }
}
