package io.guestgraph.engine.api;

import io.guestgraph.engine.domain.IdentifierType;
import io.guestgraph.engine.domain.MergeEvent;
import io.guestgraph.engine.domain.NormalizedIdentifier;
import io.guestgraph.engine.domain.SourceRecord;
import io.guestgraph.engine.persistence.GuestQueryService;
import io.guestgraph.engine.persistence.GuestQueryService.GuestView;
import io.guestgraph.engine.persistence.Jsons;
import io.guestgraph.engine.resolution.GraphMutationService;
import io.guestgraph.engine.resolution.GuestIdResolution;
import io.guestgraph.engine.resolution.GuestIdStatus;
import io.guestgraph.engine.resolution.ResolutionHop;
import io.guestgraph.engine.resolution.UnmergeOperation;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read side of /api/v1/guests (US2): golden profile + identifiers, source records verbatim, lookup
 * by normalized identifier. Cross-tenant ids answer as not-found (Constitution I).
 */
@RestController
@RequestMapping("/api/v1/guests")
public class GuestController {

  public record IdentifierDto(String type, String value) {
    static IdentifierDto of(NormalizedIdentifier identifier) {
      return new IdentifierDto(identifier.type().name(), identifier.value());
    }
  }

  /** An active guest. {@code status} is the one field shared with {@link GuestIdResolutionDto}. */
  public record GuestResponse(
      UUID id,
      String status,
      Map<String, Object> profile,
      List<IdentifierDto> identifiers,
      int recordCount,
      Instant createdAt,
      Instant updatedAt) {

    static GuestResponse of(GuestView view) {
      return new GuestResponse(
          view.guest().id(),
          GuestIdStatus.ACTIVE.name(),
          view.guest().profile(),
          view.identifiers().stream().map(IdentifierDto::of).toList(),
          view.recordCount(),
          view.guest().createdAt(),
          view.guest().updatedAt());
    }
  }

  /** A retired id: no profile, no identifiers — where the person went (spec 004). */
  public record GuestIdResolutionDto(
      UUID id,
      String status,
      List<UUID> currentGuestIds,
      Instant retiredAt,
      List<ResolutionHopDto> hops) {

    static GuestIdResolutionDto of(GuestIdResolution resolution) {
      return new GuestIdResolutionDto(
          resolution.id(),
          resolution.status().name(),
          resolution.currentGuestIds(),
          resolution.retiredAt(),
          resolution.hops().stream().map(ResolutionHopDto::of).toList());
    }
  }

  public record ResolutionHopDto(
      UUID retiredGuestId, String kind, UUID eventId, Instant at, List<UUID> successorGuestIds) {

    static ResolutionHopDto of(ResolutionHop hop) {
      return new ResolutionHopDto(
          hop.retiredGuestId(),
          hop.kind().name(),
          hop.eventId(),
          hop.at(),
          hop.successorGuestIds());
    }
  }

  public record SourceRecordResponse(
      UUID id,
      String sourceSystem,
      String externalKey,
      Map<String, Object> payload,
      List<IdentifierDto> identifiers,
      Instant recordTimestamp,
      boolean needsReview,
      List<String> needsReviewReasons,
      Instant receivedAt) {}

  public record MergeEventDto(
      UUID id,
      String kind,
      UUID guestId,
      List<UUID> absorbedGuestIds,
      List<UUID> sourceRecordIds,
      String matcherName,
      BigDecimal confidence,
      Map<String, Object> evidence,
      List<UUID> excludedGuestIds,
      ActorDto actor,
      Instant createdAt) {

    static MergeEventDto of(MergeEvent event) {
      return new MergeEventDto(
          event.id(),
          event.kind().name(),
          event.guestId(),
          event.absorbedGuestIds(),
          event.sourceRecordIds(),
          event.matcherName(),
          event.confidence(),
          event.evidence(),
          event.excludedGuestIds(),
          ActorDto.of(event.actor()),
          event.createdAt());
    }
  }

  public record UnmergeRequest(List<UUID> sourceRecordIds) {}

  public record DetachedRecordDto(UUID sourceRecordId, UUID guestId) {}

  public record UnmergeResponse(
      UUID unmergeEventId, UUID remainingGuestId, List<DetachedRecordDto> detachedRecords) {}

  private final GuestQueryService queryService;
  private final GraphMutationService mutationService;
  private final GuestGate gate;
  private final Jsons jsons;

  public GuestController(
      GuestQueryService queryService,
      GraphMutationService mutationService,
      GuestGate gate,
      Jsons jsons) {
    this.queryService = queryService;
    this.mutationService = mutationService;
    this.gate = gate;
    this.jsons = jsons;
  }

  /**
   * An active guest, or — for an id a merge absorbed or an unmerge emptied — where the person is
   * now. Both answer 200 and carry {@code status}; only an id that never existed here is 404.
   */
  @GetMapping("/{guestId}")
  public Object getGuest(@PathVariable UUID guestId) {
    UUID tenantId = TenantContext.tenantId();
    Optional<GuestView> guest = queryService.findGuest(tenantId, guestId);
    if (guest.isPresent()) {
      return GuestResponse.of(guest.get());
    }
    return mutationService
        .resolve(tenantId, guestId)
        .map(GuestIdResolutionDto::of)
        .orElseThrow(() -> new NotFoundException("No guest " + guestId + " in this tenant"));
  }

  @GetMapping("/{guestId}/records")
  public Map<String, Object> getGuestRecords(@PathVariable UUID guestId) {
    UUID tenantId = TenantContext.tenantId();
    gate.require(tenantId, guestId);
    List<SourceRecord> records = queryService.recordsOf(tenantId, guestId);
    return Map.of("records", records.stream().map(this::toResponse).toList());
  }

  @GetMapping("/{guestId}/explain")
  public Map<String, Object> explain(@PathVariable UUID guestId) {
    UUID tenantId = TenantContext.tenantId();
    gate.require(tenantId, guestId);
    List<MergeEvent> chain = mutationService.explain(tenantId, guestId);
    return Map.of("guestId", guestId, "events", chain.stream().map(MergeEventDto::of).toList());
  }

  @PostMapping("/{guestId}/unmerge")
  public UnmergeResponse unmerge(@PathVariable UUID guestId, @RequestBody UnmergeRequest request) {
    if (request.sourceRecordIds() == null || request.sourceRecordIds().isEmpty()) {
      throw new BadRequestException("Field 'sourceRecordIds' must contain at least one record id");
    }
    UUID tenantId = TenantContext.tenantId();
    gate.require(tenantId, guestId);
    UnmergeOperation.UnmergeResult result =
        mutationService.unmerge(
            tenantId, guestId, request.sourceRecordIds(), ActorResolver.actor());
    return new UnmergeResponse(
        result.unmergeEventId(),
        result.remainingGuestId(),
        result.detachedRecordToGuest().entrySet().stream()
            .map(e -> new DetachedRecordDto(e.getKey(), e.getValue()))
            .toList());
  }

  @GetMapping
  public Map<String, Object> lookup(
      @RequestParam("identifier") String identifier,
      @RequestParam(value = "type", required = false) IdentifierType type) {
    if (identifier.isBlank()) {
      throw new BadRequestException("Query parameter 'identifier' must not be blank");
    }
    List<GuestView> guests = queryService.lookup(TenantContext.tenantId(), identifier, type);
    return Map.of("guests", guests.stream().map(GuestResponse::of).toList());
  }

  private SourceRecordResponse toResponse(SourceRecord record) {
    return new SourceRecordResponse(
        record.id(),
        record.sourceSystemCode(),
        record.externalKey(),
        // The original payload, parsed back verbatim from the immutable jsonb column.
        jsons.map(record.payloadJson()),
        record.identifiers().stream().map(IdentifierDto::of).toList(),
        record.recordTimestamp(),
        record.needsReview(),
        record.needsReviewReasons(),
        record.receivedAt());
  }
}
