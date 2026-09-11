package io.guestgraph.engine.resolution;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Where the person a guest id referred to is now. {@code currentGuestIds} holds every active guest
 * the walk ended at, each once, in first-reached order; {@code hops} every retirement crossed, in
 * the order they happened. For an {@link GuestIdStatus#ACTIVE} id both are empty and {@code
 * retiredAt} is null.
 */
public record GuestIdResolution(
    UUID id,
    GuestIdStatus status,
    List<UUID> currentGuestIds,
    Instant retiredAt,
    List<ResolutionHop> hops) {

  public static GuestIdResolution active(UUID id) {
    return new GuestIdResolution(id, GuestIdStatus.ACTIVE, List.of(), null, List.of());
  }
}
