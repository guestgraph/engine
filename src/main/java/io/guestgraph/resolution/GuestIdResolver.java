package io.guestgraph.resolution;

import io.guestgraph.domain.MergeEvent;
import io.guestgraph.domain.MergeEventKind;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Where is the person this guest id referred to? A retired id — absorbed by a merge or emptied by
 * an unmerge — is followed through the append-only merge events to the active guest or guests that
 * hold the person now (spec 004, data-model derivation rules). Pure JVM behind {@link GraphPort};
 * nothing is written.
 */
public class GuestIdResolver {

  private static final Logger log = LoggerFactory.getLogger(GuestIdResolver.class);

  /**
   * The split hop reads the tenant's events forward from the unmerge in pages of this size. The
   * replay events it looks for share the unmerge's transaction, so they sit within the first page
   * in practice; paging only bounds memory if they ever do not.
   */
  static final int PAGE = 200;

  /**
   * Latest retirement wins: ids are never reused, so the last candidate is the one after which the
   * guest was gone.
   */
  private static final Comparator<MergeEvent> EVENT_ORDER =
      Comparator.comparing(MergeEvent::createdAt).thenComparing(MergeEvent::id);

  private final GraphPort graph;
  private final int pageSize;

  public GuestIdResolver(GraphPort graph) {
    this(graph, PAGE);
  }

  /** Tests pin the paging with a page of one; production uses {@link #PAGE}. */
  GuestIdResolver(GraphPort graph, int pageSize) {
    this.graph = graph;
    this.pageSize = pageSize;
  }

  /** Empty when the id never existed in the tenant — the caller's not-found. */
  public Optional<GuestIdResolution> resolve(UUID tenantId, UUID guestId) {
    if (graph.guestExists(tenantId, guestId)) {
      return Optional.of(GuestIdResolution.active(guestId));
    }
    Optional<MergeEvent> retirement = retirementOf(tenantId, guestId);
    if (retirement.isEmpty()) {
      return Optional.empty();
    }

    List<ResolutionHop> hops = new ArrayList<>();
    Set<UUID> current = new LinkedHashSet<>();
    Set<UUID> expanded = new LinkedHashSet<>();
    Deque<UUID> frontier = new ArrayDeque<>();
    frontier.add(guestId);
    while (!frontier.isEmpty()) {
      UUID retired = frontier.poll();
      if (!expanded.add(retired)) {
        continue;
      }
      Optional<MergeEvent> event =
          retired.equals(guestId) ? retirement : retirementOf(tenantId, retired);
      if (event.isEmpty()) {
        // Rule 4: a successor with neither a guest nor a retirement is reported, not invented.
        log.warn(
            "Guest {} in tenant {} was named as a successor but neither exists nor was retired",
            retired,
            tenantId);
        continue;
      }
      ResolutionHop hop = hopOf(tenantId, event.get(), retired);
      hops.add(hop);
      for (UUID successor : hop.successorGuestIds()) {
        if (graph.guestExists(tenantId, successor)) {
          current.add(successor);
        } else {
          frontier.add(successor);
        }
      }
    }
    hops.sort(Comparator.comparing(ResolutionHop::at));

    GuestIdStatus status =
        switch (current.size()) {
          case 0 -> GuestIdStatus.RETIRED;
          case 1 -> GuestIdStatus.MERGED;
          default -> GuestIdStatus.SPLIT;
        };
    return Optional.of(
        new GuestIdResolution(
            guestId,
            status,
            List.copyOf(current),
            retirement.get().createdAt(),
            List.copyOf(hops)));
  }

  /**
   * Rule 2: the latest candidate is the retirement. Candidates are the merges that absorbed the
   * guest and the unmerges made on it; a partial unmerge that left the guest alive is always
   * followed by a later event, so it is never the latest.
   */
  private Optional<MergeEvent> retirementOf(UUID tenantId, UUID guestId) {
    Stream<MergeEvent> unmerges =
        graph.eventsForGuests(tenantId, List.of(guestId)).stream()
            .filter(e -> e.kind() == MergeEventKind.UNMERGE);
    return Stream.concat(graph.eventsAbsorbing(tenantId, guestId).stream(), unmerges)
        .max(EVENT_ORDER);
  }

  /** Rule 3: a merge's successor is its survivor; a split's are where its records landed. */
  private ResolutionHop hopOf(UUID tenantId, MergeEvent event, UUID retired) {
    return switch (event.kind()) {
      case MERGE, REVIEW_CONFIRM ->
          new ResolutionHop(
              retired,
              ResolutionHop.Kind.MERGE,
              event.id(),
              event.createdAt(),
              List.of(event.guestId()));
      case UNMERGE ->
          new ResolutionHop(
              retired,
              ResolutionHop.Kind.SPLIT,
              event.id(),
              event.createdAt(),
              landingsOf(tenantId, event));
      default ->
          throw new IllegalStateException(
              "Event " + event.id() + " of kind " + event.kind() + " cannot retire a guest");
    };
  }

  /**
   * The guest each detached record landed on: the first event at or after the unmerge whose record
   * list names it. The replay writes exactly one such event per record inside the unmerge's
   * transaction, so the scan stops after a handful of rows; a record never found contributes no
   * successor and is logged.
   */
  private List<UUID> landingsOf(UUID tenantId, MergeEvent unmerge) {
    Map<UUID, UUID> landingByRecord = new LinkedHashMap<>();
    for (UUID recordId : unmerge.sourceRecordIds()) {
      landingByRecord.put(recordId, null);
    }
    int missing = landingByRecord.size();
    Instant from = unmerge.createdAt();
    UUID afterId = null;
    while (missing > 0) {
      List<MergeEvent> page = graph.eventsSince(tenantId, from, afterId, pageSize);
      for (MergeEvent event : page) {
        if (event.id().equals(unmerge.id())) {
          continue;
        }
        for (UUID recordId : event.sourceRecordIds()) {
          if (landingByRecord.containsKey(recordId) && landingByRecord.get(recordId) == null) {
            landingByRecord.put(recordId, event.guestId());
            missing--;
          }
        }
      }
      if (page.size() < pageSize) {
        break;
      }
      MergeEvent last = page.getLast();
      from = last.createdAt();
      afterId = last.id();
    }
    Set<UUID> landings = new LinkedHashSet<>();
    landingByRecord.forEach(
        (recordId, guestId) -> {
          if (guestId == null) {
            log.warn(
                "Record {} detached by unmerge in tenant {} has no replay event after it",
                recordId,
                tenantId);
          } else {
            landings.add(guestId);
          }
        });
    return List.copyOf(landings);
  }
}
