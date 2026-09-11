package io.guestgraph.engine.resolution;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One retirement on the way from a read id to a current guest. {@code successorGuestIds} is the
 * survivor for a merge and the guests the detached records landed on for a split.
 */
public record ResolutionHop(
    UUID retiredGuestId, Kind kind, UUID eventId, Instant at, List<UUID> successorGuestIds) {

  public enum Kind {
    /** A {@code MERGE} or {@code REVIEW_CONFIRM} event absorbed the guest. */
    MERGE,
    /** An {@code UNMERGE} detached every record and the guest was removed. */
    SPLIT
  }
}
