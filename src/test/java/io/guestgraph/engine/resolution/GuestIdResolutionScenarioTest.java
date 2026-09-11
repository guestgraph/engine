package io.guestgraph.engine.resolution;

import static org.assertj.core.api.Assertions.assertThat;

import io.guestgraph.engine.domain.Actor;
import io.guestgraph.engine.domain.IngestStatus;
import io.guestgraph.engine.domain.MergeEvent;
import io.guestgraph.engine.domain.MergeEventKind;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Table-driven scenarios for the retired-id walk (Constitution VI): merges and splits in → expected
 * resolution out. Pure JVM — no Spring, no database.
 */
class GuestIdResolutionScenarioTest {

  private static final UUID TENANT = EngineFixture.TENANT;

  @Test
  @DisplayName("an active guest resolves ACTIVE with no hops")
  void activeGuestResolvesActive() {
    EngineFixture fx = new EngineFixture();
    UUID guest = fx.record("a").email("anna@example.com").resolve().guestId();

    GuestIdResolution resolution = resolve(fx, guest).orElseThrow();

    assertThat(resolution.status()).isEqualTo(GuestIdStatus.ACTIVE);
    assertThat(resolution.id()).isEqualTo(guest);
    assertThat(resolution.currentGuestIds()).isEmpty();
    assertThat(resolution.hops()).isEmpty();
    assertThat(resolution.retiredAt()).isNull();
  }

  @Test
  @DisplayName("a guest absorbed by an ingest merge resolves MERGED to the survivor")
  void absorbedGuestResolvesToSurvivor() {
    EngineFixture fx = new EngineFixture();
    UUID x = fx.record("a").email("anna@example.com").resolve().guestId();
    UUID y = fx.record("b").phone("+41791112233").resolve().guestId();
    ResolutionOutcome merged =
        fx.record("c").email("anna@example.com").phone("+41791112233").resolve();
    assertThat(merged.status()).isEqualTo(IngestStatus.MERGED);
    UUID survivor = merged.guestId();
    UUID absorbed = survivor.equals(x) ? y : x;
    MergeEvent mergeEvent = lastEventOfKind(fx, MergeEventKind.MERGE);

    GuestIdResolution resolution = resolve(fx, absorbed).orElseThrow();

    assertThat(resolution.status()).isEqualTo(GuestIdStatus.MERGED);
    assertThat(resolution.currentGuestIds()).containsExactly(survivor);
    assertThat(resolution.retiredAt()).isEqualTo(mergeEvent.createdAt());
    assertThat(resolution.hops()).hasSize(1);
    ResolutionHop hop = resolution.hops().getFirst();
    assertThat(hop.retiredGuestId()).isEqualTo(absorbed);
    assertThat(hop.kind()).isEqualTo(ResolutionHop.Kind.MERGE);
    assertThat(hop.eventId()).isEqualTo(mergeEvent.id());
    assertThat(hop.at()).isEqualTo(mergeEvent.createdAt());
    assertThat(hop.successorGuestIds()).containsExactly(survivor);
    // The survivor itself is untouched.
    assertThat(resolve(fx, survivor).orElseThrow().status()).isEqualTo(GuestIdStatus.ACTIVE);
  }

  @Test
  @DisplayName("a guest absorbed by a confirmed review resolves MERGED the same way")
  void reviewConfirmedMergeResolvesToSurvivor() {
    EngineFixture fx = new EngineFixture();
    fx.graph.setReviewThreshold(1);
    UUID candidate = fx.record("c1").email("crowded@example.com").resolve().guestId();
    ResolutionOutcome parked = fx.record("c2").email("crowded@example.com").resolve();
    assertThat(parked.pendingReviewIds()).hasSize(1);
    UUID parkedGuest = parked.guestId();
    assertThat(parkedGuest).isNotEqualTo(candidate);

    new ReviewDecisionOperation(fx.graph, fx.engine)
        .decide(TENANT, parked.pendingReviewIds().getFirst(), true, EngineFixture.ACTOR);
    MergeEvent confirm = lastEventOfKind(fx, MergeEventKind.REVIEW_CONFIRM);

    GuestIdResolution resolution = resolve(fx, parkedGuest).orElseThrow();

    assertThat(resolution.status()).isEqualTo(GuestIdStatus.MERGED);
    assertThat(resolution.currentGuestIds()).containsExactly(candidate);
    assertThat(resolution.hops().getFirst().eventId()).isEqualTo(confirm.id());
    assertThat(resolution.hops().getFirst().kind()).isEqualTo(ResolutionHop.Kind.MERGE);
  }

  @Test
  @DisplayName("an id that never existed resolves as never-existed, not as retired")
  void unknownIdIsNeverExisted() {
    EngineFixture fx = new EngineFixture();
    fx.record("a").email("anna@example.com").resolve();

    assertThat(resolve(fx, UUID.randomUUID())).isEmpty();
  }

  @Test
  @DisplayName("an id retired in another tenant is never-existed here (Constitution I)")
  void otherTenantsRetirementIsInvisible() {
    EngineFixture fx = new EngineFixture();
    UUID x = fx.record("a").email("anna@example.com").resolve().guestId();
    UUID y = fx.record("b").phone("+41791112233").resolve().guestId();
    UUID survivor =
        fx.record("c").email("anna@example.com").phone("+41791112233").resolve().guestId();
    UUID absorbed = survivor.equals(x) ? y : x;
    UUID otherTenant = UUID.nameUUIDFromBytes("tenant:other".getBytes());

    assertThat(new GuestIdResolver(fx.graph).resolve(otherTenant, absorbed)).isEmpty();
    assertThat(new GuestIdResolver(fx.graph).resolve(otherTenant, survivor)).isEmpty();
  }

  // --- US2: chains are followed to the end ------------------------------------------------

  @Test
  @DisplayName("X absorbed into Y, Y into Z: X resolves to Z with two hops in order")
  void chainIsFollowedToTheEnd() {
    EngineFixture fx = new EngineFixture();
    List<UUID> chain = mergeChain(fx, 3);
    UUID x = chain.get(0);
    UUID y = chain.get(1);
    UUID z = chain.get(2);
    assertThat(resolve(fx, z).orElseThrow().status()).isEqualTo(GuestIdStatus.ACTIVE);

    GuestIdResolution fromX = resolve(fx, x).orElseThrow();

    assertThat(fromX.status()).isEqualTo(GuestIdStatus.MERGED);
    assertThat(fromX.currentGuestIds()).containsExactly(z);
    assertThat(fromX.hops()).extracting(ResolutionHop::retiredGuestId).containsExactly(x, y);
    assertThat(fromX.hops())
        .extracting(ResolutionHop::successorGuestIds)
        .containsExactly(List.of(y), List.of(z));
    assertThat(fromX.hops().get(0).at()).isBeforeOrEqualTo(fromX.hops().get(1).at());
    assertThat(fromX.retiredAt()).isEqualTo(fromX.hops().get(0).at());

    GuestIdResolution fromY = resolve(fx, y).orElseThrow();
    assertThat(fromY.currentGuestIds()).containsExactly(z);
    assertThat(fromY.hops()).hasSize(1);
    assertThat(fromY.hops().getFirst().retiredGuestId()).isEqualTo(y);
  }

  @Test
  @DisplayName("a chain of ten merges resolves to its end and never names a retired guest")
  void longChainResolvesToItsEnd() {
    EngineFixture fx = new EngineFixture();
    List<UUID> chain = mergeChain(fx, 11);

    GuestIdResolution resolution = resolve(fx, chain.getFirst()).orElseThrow();

    assertThat(resolution.status()).isEqualTo(GuestIdStatus.MERGED);
    assertThat(resolution.currentGuestIds()).containsExactly(chain.getLast());
    assertThat(resolution.hops()).hasSize(10);
    assertThat(resolution.hops())
        .extracting(ResolutionHop::retiredGuestId)
        .containsExactlyElementsOf(chain.subList(0, 10));
    for (UUID current : resolution.currentGuestIds()) {
      assertThat(fx.graph.guestExists(TENANT, current)).isTrue();
    }
  }

  // --- US3: a split guest resolves to every guest it became --------------------------------

  @Test
  @DisplayName("a guest emptied by an unmerge resolves SPLIT to every guest its records landed on")
  void emptiedGuestResolvesToEveryLanding() {
    EngineFixture fx = new EngineFixture();
    Split split = confirmedPairThenSplit(fx);

    GuestIdResolution resolution = resolve(fx, split.emptied).orElseThrow();

    assertThat(resolution.status()).isEqualTo(GuestIdStatus.SPLIT);
    assertThat(resolution.currentGuestIds()).containsExactlyInAnyOrder(split.w1, split.w2);
    assertThat(resolution.retiredAt()).isEqualTo(split.unmergeEvent.createdAt());
    assertThat(resolution.hops()).hasSize(1);
    ResolutionHop hop = resolution.hops().getFirst();
    assertThat(hop.kind()).isEqualTo(ResolutionHop.Kind.SPLIT);
    assertThat(hop.retiredGuestId()).isEqualTo(split.emptied);
    assertThat(hop.eventId()).isEqualTo(split.unmergeEvent.id());
    assertThat(hop.successorGuestIds()).containsExactly(split.w1, split.w2);
  }

  @Test
  @DisplayName("a split branch that later merges is followed to its survivor")
  void splitBranchLaterMergedIsFollowed() {
    EngineFixture fx = new EngineFixture();
    Split split = confirmedPairThenSplit(fx);
    fx.graph.setReviewThreshold(1000);
    UUID v = fx.record("v").phone("+41799999999").resolve().guestId();
    // Phone first, so V is the survivor and W1 (matched via its loyalty id) is absorbed.
    ResolutionOutcome merged =
        fx.record("bridge-v").phone("+41799999999").loyaltyId("L1").resolve();
    assertThat(merged.status()).isEqualTo(IngestStatus.MERGED);
    assertThat(merged.guestId()).isEqualTo(v);

    GuestIdResolution resolution = resolve(fx, split.emptied).orElseThrow();

    assertThat(resolution.status()).isEqualTo(GuestIdStatus.SPLIT);
    assertThat(resolution.currentGuestIds()).containsExactlyInAnyOrder(v, split.w2);
    assertThat(resolution.hops())
        .extracting(ResolutionHop::kind)
        .containsExactly(ResolutionHop.Kind.SPLIT, ResolutionHop.Kind.MERGE);
    assertThat(resolution.hops().get(1).retiredGuestId()).isEqualTo(split.w1);
  }

  @Test
  @DisplayName("split branches that re-merge into one guest resolve MERGED, the guest named once")
  void rejoinedBranchesResolveMergedOnce() {
    EngineFixture fx = new EngineFixture();
    Split split = confirmedPairThenSplit(fx);
    fx.graph.setReviewThreshold(1000);
    // With the identifier no longer crowded, a record carrying it matches both branches.
    ResolutionOutcome rejoined = fx.record("rejoin").email("crowded@example.com").resolve();
    assertThat(rejoined.status()).isEqualTo(IngestStatus.MERGED);

    GuestIdResolution resolution = resolve(fx, split.emptied).orElseThrow();

    assertThat(resolution.status()).isEqualTo(GuestIdStatus.MERGED);
    assertThat(resolution.currentGuestIds()).containsExactly(rejoined.guestId());
    assertThat(resolution.hops()).hasSize(2);
  }

  @Test
  @DisplayName("a merge into a guest that is later emptied fans out through both")
  void mergeThenSplitFansOut() {
    EngineFixture fx = new EngineFixture();
    fx.graph.setReviewThreshold(1000);
    UUID x = fx.record("x").phone("+41791111111").resolve().guestId();
    UUID y = fx.record("y").email("y@example.com").resolve().guestId();
    ResolutionOutcome merged =
        fx.record("bridge").email("y@example.com").phone("+41791111111").resolve();
    assertThat(merged.guestId()).isEqualTo(y);
    // Detach everything from Y; each record re-resolves alone once the identifiers are crowded.
    fx.graph.setReviewThreshold(1);
    new UnmergeOperation(fx.graph, fx.engine)
        .unmerge(
            TENANT,
            y,
            List.of(fx.recordId("x"), fx.recordId("y"), fx.recordId("bridge")),
            EngineFixture.ACTOR);
    assertThat(fx.graph.guestExists(TENANT, y)).isFalse();

    GuestIdResolution resolution = resolve(fx, x).orElseThrow();

    assertThat(resolution.status()).isEqualTo(GuestIdStatus.SPLIT);
    assertThat(resolution.currentGuestIds()).hasSizeGreaterThan(1);
    assertThat(resolution.hops())
        .extracting(ResolutionHop::kind)
        .containsExactly(ResolutionHop.Kind.MERGE, ResolutionHop.Kind.SPLIT);
    assertThat(resolution.hops().get(0).retiredGuestId()).isEqualTo(x);
    assertThat(resolution.hops().get(1).retiredGuestId()).isEqualTo(y);
    for (UUID current : resolution.currentGuestIds()) {
      assertThat(fx.graph.guestExists(TENANT, current)).isTrue();
    }
  }

  @Test
  @DisplayName("a partial unmerge leaves the guest active")
  void partialUnmergeKeepsGuestActive() {
    EngineFixture fx = new EngineFixture();
    UUID guest = fx.record("r1").email("anna@example.com").resolve().guestId();
    fx.record("r2").email("anna@example.com").resolve();
    fx.record("r3").email("anna@example.com").resolve();

    new UnmergeOperation(fx.graph, fx.engine)
        .unmerge(TENANT, guest, List.of(fx.recordId("r3")), EngineFixture.ACTOR);

    assertThat(resolve(fx, guest).orElseThrow().status()).isEqualTo(GuestIdStatus.ACTIVE);
  }

  @Test
  @DisplayName("a retirement whose successor neither exists nor was retired resolves RETIRED")
  void danglingChainReportsRetired() {
    EngineFixture fx = new EngineFixture();
    UUID x = fx.record("x").email("x@example.com").resolve().guestId();
    UUID ghost = UUID.randomUUID();
    // An inconsistent trail, built by hand: X absorbed into a survivor that was never created.
    fx.graph.saveEvent(event(MergeEventKind.MERGE, ghost, List.of(x), List.of(), Instant.now()));
    fx.graph.deleteGuest(TENANT, x);

    GuestIdResolution resolution = resolve(fx, x).orElseThrow();

    assertThat(resolution.status()).isEqualTo(GuestIdStatus.RETIRED);
    assertThat(resolution.currentGuestIds()).isEmpty();
    assertThat(resolution.hops()).hasSize(1);
    assertThat(resolution.hops().getFirst().successorGuestIds()).containsExactly(ghost);
  }

  // --- SC-004: the integrator rule holds across mixed sequences ----------------------------

  /**
   * Twenty-four distinct sequences of merges and splits — chain length 2 to 5, times three endings,
   * times the held id being the chain's first or its middle guest — each followed by the rule an
   * integrator is told: on MERGED replace the stored id with the current one. After every sequence
   * the held id must resolve ACTIVE (SC-004), and every current guest named on the way must exist
   * (SC-002). On SPLIT the rule says escalate; the first branch stands in for that choice here so
   * the walk after it is still exercised.
   */
  @Test
  @DisplayName("an integrator that replaces its stored id on MERGED always holds a valid id")
  void integratorRuleHoldsAcrossSequences() {
    int sequences = 0;
    for (int seed = 0; seed < 24; seed++) {
      EngineFixture fx = new EngineFixture();
      List<UUID> chain = mergeChain(fx, 2 + seed % 4);
      UUID held = seed / 12 == 0 ? chain.getFirst() : chain.get(chain.size() / 2);
      if (seed % 3 == 1) {
        // Empty the chain's survivor by detaching every record it holds.
        UUID survivor = chain.getLast();
        List<UUID> records = fx.graph.recordIdsOfGuest(TENANT, survivor);
        fx.graph.setReviewThreshold(1);
        new UnmergeOperation(fx.graph, fx.engine)
            .unmerge(TENANT, survivor, records, EngineFixture.ACTOR);
      }
      if (seed % 3 == 2) {
        // Merge the chain's survivor onward once more, then a partial unmerge.
        fx.graph.setReviewThreshold(1000);
        UUID next = fx.record("tail").email("tail@example.com").resolve().guestId();
        fx.record("tail-bridge").email("tail@example.com").phone("+41790000000").resolve();
        List<UUID> records = fx.graph.recordIdsOfGuest(TENANT, next);
        new UnmergeOperation(fx.graph, fx.engine)
            .unmerge(TENANT, next, records.subList(0, 1), EngineFixture.ACTOR);
      }

      // The integrator rule: read, and on MERGED take the one current id.
      GuestIdResolution resolution = resolve(fx, held).orElseThrow();
      for (UUID current : resolution.currentGuestIds()) {
        assertThat(fx.graph.guestExists(TENANT, current)).as("seed %d", seed).isTrue();
      }
      if (resolution.status() == GuestIdStatus.MERGED) {
        held = resolution.currentGuestIds().getFirst();
      } else if (resolution.status() == GuestIdStatus.SPLIT) {
        // A split is escalated in practice; here the first branch stands in for the choice.
        held = resolution.currentGuestIds().getFirst();
      }
      assertThat(resolve(fx, held).orElseThrow().status())
          .as("seed %d after applying the rule", seed)
          .isEqualTo(GuestIdStatus.ACTIVE);
      sequences++;
    }
    assertThat(sequences).isGreaterThanOrEqualTo(20);
  }

  @Test
  @DisplayName("a replay event sharing the unmerge's timestamp with a smaller id is still found")
  void replayEventTiedOnTimestampIsFound() {
    EngineFixture fx = new EngineFixture();
    UUID emptied = fx.graph.createGuest(TENANT).id();
    UUID landing = fx.graph.createGuest(TENANT).id();
    UUID record = UUID.randomUUID();
    Instant at = Instant.parse("2026-09-09T10:00:00.000001Z");
    // Ids chosen so the replay sorts before the unmerge at the same instant, in both backends.
    UUID unmergeId = UUID.fromString("ffffffff-ffff-4fff-8fff-ffffffffffff");
    UUID replayId = UUID.fromString("00000000-0000-4000-8000-000000000001");
    fx.graph.saveEvent(
        event(unmergeId, MergeEventKind.UNMERGE, emptied, List.of(), List.of(record), at));
    fx.graph.saveEvent(
        event(replayId, MergeEventKind.CREATE, landing, List.of(), List.of(record), at));
    fx.graph.deleteGuest(TENANT, emptied);

    GuestIdResolution resolution = resolve(fx, emptied).orElseThrow();

    assertThat(resolution.status()).isEqualTo(GuestIdStatus.MERGED);
    assertThat(resolution.currentGuestIds()).containsExactly(landing);
  }

  @Test
  @DisplayName("the split hop pages forward when the replay events sit beyond the first page")
  void splitHopPagesForward() {
    EngineFixture fx = new EngineFixture();
    UUID emptied = fx.graph.createGuest(TENANT).id();
    UUID landing1 = fx.graph.createGuest(TENANT).id();
    UUID landing2 = fx.graph.createGuest(TENANT).id();
    UUID bystander = fx.graph.createGuest(TENANT).id();
    UUID r1 = UUID.randomUUID();
    UUID r2 = UUID.randomUUID();
    Instant at = Instant.parse("2026-09-09T10:00:00Z");
    fx.graph.saveEvent(event(MergeEventKind.UNMERGE, emptied, List.of(), List.of(r1, r2), at));
    for (int i = 1; i <= 5; i++) {
      // Unrelated events between the unmerge and its replay, each on its own page of one.
      fx.graph.saveEvent(
          event(
              MergeEventKind.ATTACH,
              bystander,
              List.of(),
              List.of(UUID.randomUUID()),
              at.plusMillis(i)));
    }
    fx.graph.saveEvent(
        event(MergeEventKind.CREATE, landing1, List.of(), List.of(r1), at.plusMillis(6)));
    fx.graph.saveEvent(
        event(MergeEventKind.CREATE, landing2, List.of(), List.of(r2), at.plusMillis(7)));
    fx.graph.deleteGuest(TENANT, emptied);

    GuestIdResolution resolution =
        new GuestIdResolver(fx.graph, 1).resolve(TENANT, emptied).orElseThrow();

    assertThat(resolution.status()).isEqualTo(GuestIdStatus.SPLIT);
    assertThat(resolution.currentGuestIds()).containsExactly(landing1, landing2);
    assertThat(resolution.hops().getFirst().successorGuestIds())
        .containsExactly(landing1, landing2);
  }

  private static MergeEvent event(
      MergeEventKind kind, UUID guestId, List<UUID> absorbed, List<UUID> records, Instant at) {
    return event(UUID.randomUUID(), kind, guestId, absorbed, records, at);
  }

  /** A hand-built event for histories the engine cannot produce on purpose. */
  private static MergeEvent event(
      UUID id,
      MergeEventKind kind,
      UUID guestId,
      List<UUID> absorbed,
      List<UUID> records,
      Instant at) {
    return new MergeEvent(
        id,
        TENANT,
        kind,
        guestId,
        absorbed,
        records,
        "hand-built",
        BigDecimal.ONE,
        Map.of(),
        List.of(),
        Actor.unattributed(),
        at);
  }

  record Split(UUID emptied, UUID w1, UUID w2, MergeEvent unmergeEvent) {}

  /**
   * A guest a steward confirmed out of a crowded identifier, then emptied. With the threshold at 1
   * the shared email parks rather than attaches, so the two detached records re-resolve onto two
   * guests: W1 carries the loyalty id, W2 does not.
   */
  private static Split confirmedPairThenSplit(EngineFixture fx) {
    fx.graph.setReviewThreshold(1);
    UUID candidate =
        fx.record("r1").email("crowded@example.com").loyaltyId("L1").resolve().guestId();
    ResolutionOutcome parked = fx.record("r2").email("crowded@example.com").resolve();
    new ReviewDecisionOperation(fx.graph, fx.engine)
        .decide(TENANT, parked.pendingReviewIds().getFirst(), true, EngineFixture.ACTOR);
    assertThat(fx.graph.linkCount(TENANT, candidate)).isEqualTo(2);

    UnmergeOperation.UnmergeResult result =
        new UnmergeOperation(fx.graph, fx.engine)
            .unmerge(
                TENANT,
                candidate,
                List.of(fx.recordId("r1"), fx.recordId("r2")),
                EngineFixture.ACTOR);
    assertThat(result.remainingGuestId()).isNull();
    UUID w1 = result.detachedRecordToGuest().get(fx.recordId("r1"));
    UUID w2 = result.detachedRecordToGuest().get(fx.recordId("r2"));
    assertThat(w1).isNotEqualTo(w2);
    MergeEvent unmerge =
        fx.graph.events().stream()
            .filter(e -> e.id().equals(result.unmergeEventId()))
            .findFirst()
            .orElseThrow();
    return new Split(candidate, w1, w2, unmerge);
  }

  /**
   * Guests g0 → g1 → … → g(n-1), each absorbed by the next. The bridge record's only email is the
   * newer guest's, and the matcher takes candidates in identifier order, so the newer guest is
   * always the survivor; a shared phone carries the chain forward.
   */
  private static List<UUID> mergeChain(EngineFixture fx, int length) {
    fx.graph.setReviewThreshold(1000);
    List<UUID> chain = new ArrayList<>();
    UUID first = fx.record("g0").phone("+41790000000").resolve().guestId();
    chain.add(first);
    for (int i = 1; i < length; i++) {
      String email = "g" + i + "@example.com";
      UUID next = fx.record("g" + i).email(email).resolve().guestId();
      ResolutionOutcome merged =
          fx.record("bridge" + i).email(email).phone("+41790000000").resolve();
      assertThat(merged.status()).isEqualTo(IngestStatus.MERGED);
      assertThat(merged.guestId()).as("the newer guest survives").isEqualTo(next);
      chain.add(next);
    }
    return chain;
  }

  private static Optional<GuestIdResolution> resolve(EngineFixture fx, UUID guestId) {
    return new GuestIdResolver(fx.graph).resolve(TENANT, guestId);
  }

  private static MergeEvent lastEventOfKind(EngineFixture fx, MergeEventKind kind) {
    return fx.graph.events().stream()
        .filter(e -> e.kind() == kind)
        .reduce((first, second) -> second)
        .orElseThrow();
  }
}
