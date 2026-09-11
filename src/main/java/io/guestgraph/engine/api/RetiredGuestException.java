package io.guestgraph.engine.api;

import io.guestgraph.engine.resolution.GuestIdResolution;
import io.guestgraph.service.ServiceException;
import org.springframework.http.HttpStatus;

/**
 * A sub-resource or operation was addressed under a guest id that a merge absorbed or an unmerge
 * emptied. Answers 410 with the current guest ids, so a client that hits a retired id anywhere
 * learns where to go and a client that ignores the hint fails loudly rather than reading stale data
 * (spec 004, FR-008).
 */
public class RetiredGuestException extends ServiceException {

  private final transient GuestIdResolution resolution;

  public RetiredGuestException(GuestIdResolution resolution) {
    super(
        HttpStatus.GONE,
        "guest-retired",
        "Guest id retired",
        "Guest "
            + resolution.id()
            + " was retired; current guest ids: "
            + resolution.currentGuestIds());
    this.resolution = resolution;
    // RFC 9457 owns the member named status, so the resolution's status is named apart.
    withProperty("guestId", resolution.id());
    withProperty("resolutionStatus", resolution.status().name());
    withProperty("currentGuestIds", resolution.currentGuestIds());
  }

  public GuestIdResolution resolution() {
    return resolution;
  }
}
