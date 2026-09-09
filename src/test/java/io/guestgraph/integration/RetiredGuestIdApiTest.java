package io.guestgraph.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

/** Spec 004 acceptance scenarios over the API, following the quickstart walk. */
class RetiredGuestIdApiTest extends PostgresIntegrationTest {

  @BeforeEach
  void registerSourceSystem() {
    api(TENANT_A_KEY)
        .post()
        .uri("/api/v1/source-systems")
        .contentType(MediaType.APPLICATION_JSON)
        .body("{\"code\":\"opera-pms\",\"name\":\"Opera\"}")
        .retrieve()
        .toEntity(String.class);
  }

  // --- US1: a stored guest id never goes dark ---------------------------------------------

  @Test
  @DisplayName("an absorbed guest id answers 200 MERGED with the survivor and the merge event")
  void absorbedIdResolvesToSurvivor() {
    Merge merge = mergeTwo("a", "anna@example.com", "b", "+41791112233");

    ResponseEntity<String> response = read("/api/v1/guests/" + merge.absorbed);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode body = json(response.getBody());
    assertThat(body.get("id").asString()).isEqualTo(merge.absorbed);
    assertThat(body.get("status").asString()).isEqualTo("MERGED");
    assertThat(body.get("currentGuestIds")).hasSize(1);
    assertThat(body.get("currentGuestIds").get(0).asString()).isEqualTo(merge.survivor);
    assertThat(body.get("retiredAt").asString()).isNotBlank();
    assertThat(body.has("profile")).isFalse();
    assertThat(body.has("identifiers")).isFalse();
    JsonNode hops = body.get("hops");
    assertThat(hops).hasSize(1);
    assertThat(hops.get(0).get("retiredGuestId").asString()).isEqualTo(merge.absorbed);
    assertThat(hops.get(0).get("kind").asString()).isEqualTo("MERGE");
    assertThat(hops.get(0).get("successorGuestIds").get(0).asString()).isEqualTo(merge.survivor);
    // The hop's event is in the survivor's explain (Constitution IV).
    String eventId = hops.get(0).get("eventId").asString();
    JsonNode explain = json(read("/api/v1/guests/" + merge.survivor + "/explain").getBody());
    List<String> eventIds = new ArrayList<>();
    for (JsonNode event : explain.get("events")) {
      eventIds.add(event.get("id").asString());
    }
    assertThat(eventIds).contains(eventId);
  }

  @Test
  @DisplayName("an active guest is unchanged but for status: ACTIVE (FR-012)")
  void activeGuestCarriesStatusAndNothingElseChanges() {
    Merge merge = mergeTwo("a", "anna@example.com", "b", "+41791112233");

    JsonNode guest = json(read("/api/v1/guests/" + merge.survivor).getBody());

    assertThat(guest.get("status").asString()).isEqualTo("ACTIVE");
    assertThat(guest.get("id").asString()).isEqualTo(merge.survivor);
    assertThat(guest.get("profile").isObject()).isTrue();
    assertThat(guest.get("identifiers")).hasSize(2);
    assertThat(guest.get("recordCount").asInt()).isEqualTo(3);
    assertThat(guest.get("createdAt").asString()).isNotBlank();
    assertThat(guest.get("updatedAt").asString()).isNotBlank();
    assertThat(guest.has("currentGuestIds")).isFalse();
    assertThat(guest.has("hops")).isFalse();
  }

  @Test
  @DisplayName("an id that never existed is the plain 404 of today, with no current guests")
  void unknownIdIsNotFound() {
    ResponseEntity<String> response = read("/api/v1/guests/" + UUID.randomUUID());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    JsonNode problem = json(response.getBody());
    assertThat(problem.get("type").asString()).endsWith("/not-found");
    assertThat(problem.has("currentGuestIds")).isFalse();
  }

  @Test
  @DisplayName("a retired id of another tenant is indistinguishable from an unknown one")
  void otherTenantSeesNotFound() {
    Merge merge = mergeTwo("a", "anna@example.com", "b", "+41791112233");

    ResponseEntity<String> response =
        api(TENANT_B_KEY)
            .get()
            .uri("/api/v1/guests/" + merge.absorbed)
            .retrieve()
            .toEntity(String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(json(response.getBody()).get("type").asString()).endsWith("/not-found");
    assertThat(json(response.getBody()).has("currentGuestIds")).isFalse();
  }

  // --- US2: chains are followed to the end -------------------------------------------------

  @Test
  @DisplayName("X into Y into Z: X answers Z with two hops, Y answers Z with one")
  void chainIsFollowedToTheEnd() {
    List<String> chain = mergeChain(3);
    String x = chain.get(0);
    String y = chain.get(1);
    String z = chain.get(2);

    JsonNode fromX = json(read("/api/v1/guests/" + x).getBody());
    assertThat(fromX.get("status").asString()).isEqualTo("MERGED");
    assertThat(fromX.get("currentGuestIds").get(0).asString()).isEqualTo(z);
    assertThat(fromX.get("hops")).hasSize(2);
    assertThat(fromX.get("hops").get(0).get("retiredGuestId").asString()).isEqualTo(x);
    assertThat(fromX.get("hops").get(0).get("successorGuestIds").get(0).asString()).isEqualTo(y);
    assertThat(fromX.get("hops").get(1).get("retiredGuestId").asString()).isEqualTo(y);
    assertThat(fromX.get("hops").get(1).get("successorGuestIds").get(0).asString()).isEqualTo(z);

    JsonNode fromY = json(read("/api/v1/guests/" + y).getBody());
    assertThat(fromY.get("currentGuestIds").get(0).asString()).isEqualTo(z);
    assertThat(fromY.get("hops")).hasSize(1);

    assertThat(json(read("/api/v1/guests/" + z).getBody()).get("status").asString())
        .isEqualTo("ACTIVE");
  }

  /**
   * SC-003 over a table of a few dozen rows: this pins the code path and the per-hop query count,
   * not the indexes — their effect at scale was measured once with EXPLAIN ANALYZE (research R3),
   * not asserted here.
   */
  @Test
  @DisplayName("a ten-hop chain resolves in under one second (SC-003)")
  void tenHopsUnderOneSecond() {
    List<String> chain = mergeChain(11);

    long started = System.nanoTime();
    JsonNode resolution = json(read("/api/v1/guests/" + chain.getFirst()).getBody());
    long elapsedMillis = (System.nanoTime() - started) / 1_000_000;

    assertThat(resolution.get("hops")).hasSize(10);
    assertThat(resolution.get("currentGuestIds").get(0).asString()).isEqualTo(chain.getLast());
    assertThat(elapsedMillis).as("ten hops in ms").isLessThan(1000);
  }

  // --- US3: a split guest resolves to every guest it became --------------------------------

  @Test
  @DisplayName("an emptied guest answers SPLIT naming every guest its records landed on")
  void emptiedGuestResolvesToEveryLanding() {
    Split split = confirmedPairThenSplit();

    JsonNode resolution = json(read("/api/v1/guests/" + split.emptied).getBody());

    assertThat(resolution.get("status").asString()).isEqualTo("SPLIT");
    assertThat(ids(resolution.get("currentGuestIds")))
        .containsExactlyInAnyOrder(split.w1, split.w2);
    assertThat(resolution.get("hops")).hasSize(1);
    assertThat(resolution.get("hops").get(0).get("kind").asString()).isEqualTo("SPLIT");
    assertThat(resolution.get("hops").get(0).get("eventId").asString())
        .isEqualTo(split.unmergeEventId);
    assertThat(ids(resolution.get("hops").get(0).get("successorGuestIds")))
        .containsExactlyInAnyOrder(split.w1, split.w2);
    for (String current : ids(resolution.get("currentGuestIds"))) {
      assertThat(json(read("/api/v1/guests/" + current).getBody()).get("status").asString())
          .isEqualTo("ACTIVE");
    }
  }

  @Test
  @DisplayName("a split branch that later merges is followed to its survivor")
  void splitBranchLaterMergedIsFollowed() {
    Split split = confirmedPairThenSplit();
    setReviewThreshold(TENANT_A, 1000);
    String v = ingest("v", "{\"phone\":\"+41799999999\"}").get("guestId").asString();
    JsonNode bridge = ingest("bridge-v", "{\"phone\":\"+41799999999\",\"loyaltyId\":\"L1\"}");
    assertThat(bridge.get("status").asString()).isEqualTo("MERGED");
    assertThat(bridge.get("guestId").asString()).isEqualTo(v);

    JsonNode resolution = json(read("/api/v1/guests/" + split.emptied).getBody());

    assertThat(resolution.get("status").asString()).isEqualTo("SPLIT");
    assertThat(ids(resolution.get("currentGuestIds"))).containsExactlyInAnyOrder(v, split.w2);
    assertThat(resolution.get("hops")).hasSize(2);
    assertThat(resolution.get("hops").get(1).get("retiredGuestId").asString()).isEqualTo(split.w1);
  }

  @Test
  @DisplayName("a partial unmerge leaves the guest ACTIVE")
  void partialUnmergeKeepsGuestActive() {
    String guest = ingest("r1", "{\"email\":\"anna@example.com\"}").get("guestId").asString();
    ingest("r2", "{\"email\":\"anna@example.com\"}");
    String third =
        ingest("r3", "{\"email\":\"anna@example.com\"}").get("sourceRecordId").asString();

    ResponseEntity<String> unmerge = unmerge(guest, List.of(third));

    assertThat(unmerge.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(json(read("/api/v1/guests/" + guest).getBody()).get("status").asString())
        .isEqualTo("ACTIVE");
  }

  // --- US4: everything under a retired id points to the current one ------------------------

  @Test
  @DisplayName(
      "records, explain, timeline and unmerge on a retired id answer 410 with the current guest")
  void subResourcesOfRetiredIdAnswerGone() {
    Merge merge = mergeTwo("a", "anna@example.com", "b", "+41791112233");
    int eventsBefore =
        json(read("/api/v1/guests/" + merge.survivor + "/explain").getBody()).get("events").size();

    List<ResponseEntity<String>> responses =
        List.of(
            read("/api/v1/guests/" + merge.absorbed + "/records"),
            read("/api/v1/guests/" + merge.absorbed + "/explain"),
            read("/api/v1/guests/" + merge.absorbed + "/timeline"),
            unmerge(merge.absorbed, List.of(UUID.randomUUID().toString())));

    for (ResponseEntity<String> response : responses) {
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.GONE);
      assertThat(response.getHeaders().getContentType().toString())
          .contains("application/problem+json");
      JsonNode problem = json(response.getBody());
      assertThat(problem.get("type").asString()).endsWith("/guest-retired");
      assertThat(problem.get("title").asString()).isEqualTo("Guest id retired");
      assertThat(problem.get("status").asInt()).isEqualTo(410);
      assertThat(problem.get("guestId").asString()).isEqualTo(merge.absorbed);
      assertThat(problem.get("resolutionStatus").asString()).isEqualTo("MERGED");
      assertThat(ids(problem.get("currentGuestIds"))).containsExactly(merge.survivor);
    }
    // The refused unmerge recorded nothing.
    int eventsAfter =
        json(read("/api/v1/guests/" + merge.survivor + "/explain").getBody()).get("events").size();
    assertThat(eventsAfter).isEqualTo(eventsBefore);
  }

  @Test
  @DisplayName("the same four on an unknown id stay the plain 404 with no current guests")
  void subResourcesOfUnknownIdStayNotFound() {
    String unknown = UUID.randomUUID().toString();

    List<ResponseEntity<String>> responses =
        List.of(
            read("/api/v1/guests/" + unknown + "/records"),
            read("/api/v1/guests/" + unknown + "/explain"),
            read("/api/v1/guests/" + unknown + "/timeline"),
            unmerge(unknown, List.of(UUID.randomUUID().toString())));

    for (ResponseEntity<String> response : responses) {
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
      JsonNode problem = json(response.getBody());
      assertThat(problem.get("type").asString()).endsWith("/not-found");
      assertThat(problem.has("currentGuestIds")).isFalse();
    }
  }

  // --- helpers ------------------------------------------------------------------------------

  record Split(String emptied, String w1, String w2, String unmergeEventId) {}

  /**
   * A guest a steward confirmed out of a crowded email, then emptied. With the threshold at 1 the
   * shared email parks rather than attaches, so the two detached records land on two guests: W1
   * carries the loyalty id, W2 does not.
   */
  private Split confirmedPairThenSplit() {
    setReviewThreshold(TENANT_A, 1);
    JsonNode first = ingest("r1", "{\"email\":\"crowded@example.com\",\"loyaltyId\":\"L1\"}");
    JsonNode second = ingest("r2", "{\"email\":\"crowded@example.com\"}");
    String candidate = first.get("guestId").asString();
    String reviewId = second.get("pendingReviewIds").get(0).asString();
    ResponseEntity<String> decided =
        api(TENANT_A_KEY)
            .post()
            .uri("/api/v1/match-reviews/" + reviewId)
            .contentType(MediaType.APPLICATION_JSON)
            .body("{\"decision\":\"CONFIRM\"}")
            .retrieve()
            .toEntity(String.class);
    assertThat(decided.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(json(read("/api/v1/guests/" + candidate).getBody()).get("recordCount").asInt())
        .isEqualTo(2);

    JsonNode result =
        json(
            unmerge(
                    candidate,
                    List.of(
                        first.get("sourceRecordId").asString(),
                        second.get("sourceRecordId").asString()))
                .getBody());
    assertThat(result.get("remainingGuestId").isNull()).isTrue();
    String w1 = result.get("detachedRecords").get(0).get("guestId").asString();
    String w2 = result.get("detachedRecords").get(1).get("guestId").asString();
    assertThat(w1).isNotEqualTo(w2);
    return new Split(candidate, w1, w2, result.get("unmergeEventId").asString());
  }

  private ResponseEntity<String> unmerge(String guestId, List<String> recordIds) {
    return api(TENANT_A_KEY)
        .post()
        .uri("/api/v1/guests/" + guestId + "/unmerge")
        .contentType(MediaType.APPLICATION_JSON)
        .body("{\"sourceRecordIds\":[\"" + String.join("\",\"", recordIds) + "\"]}")
        .retrieve()
        .toEntity(String.class);
  }

  private static List<String> ids(JsonNode array) {
    List<String> ids = new ArrayList<>();
    for (JsonNode id : array) {
      ids.add(id.asString());
    }
    return ids;
  }

  /**
   * Guests g0 → g1 → … each absorbed by the next. The extractor orders identifiers email first, so
   * a bridge whose only email is the newer guest's makes that guest the survivor; a shared phone
   * carries the chain forward. The sharing threshold is raised so the phone never parks.
   */
  private List<String> mergeChain(int length) {
    setReviewThreshold(TENANT_A, 1000);
    List<String> chain = new ArrayList<>();
    chain.add(ingest("g0", "{\"phone\":\"+41790000000\"}").get("guestId").asString());
    for (int i = 1; i < length; i++) {
      String email = "g" + i + "@example.com";
      String next = ingest("g" + i, "{\"email\":\"" + email + "\"}").get("guestId").asString();
      JsonNode bridge =
          ingest("bridge" + i, "{\"email\":\"" + email + "\",\"phone\":\"+41790000000\"}");
      assertThat(bridge.get("status").asString()).isEqualTo("MERGED");
      assertThat(bridge.get("guestId").asString()).as("the newer guest survives").isEqualTo(next);
      chain.add(next);
    }
    return chain;
  }

  record Merge(String survivor, String absorbed) {}

  /** Two records that resolve apart, then one carrying both identifiers: one guest absorbs. */
  private Merge mergeTwo(String keyA, String email, String keyB, String phone) {
    String x = ingest(keyA, "{\"email\":\"" + email + "\"}").get("guestId").asString();
    String y = ingest(keyB, "{\"phone\":\"" + phone + "\"}").get("guestId").asString();
    JsonNode bridge =
        ingest(keyA + keyB, "{\"email\":\"" + email + "\",\"phone\":\"" + phone + "\"}");
    assertThat(bridge.get("status").asString()).isEqualTo("MERGED");
    String survivor = bridge.get("guestId").asString();
    return new Merge(survivor, survivor.equals(x) ? y : x);
  }

  private JsonNode ingest(String externalKey, String payload) {
    ResponseEntity<String> response =
        api(TENANT_A_KEY)
            .post()
            .uri("/api/v1/records")
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                "{\"sourceSystem\":\"opera-pms\",\"externalKey\":\""
                    + externalKey
                    + "\",\"payload\":"
                    + payload
                    + "}")
            .retrieve()
            .toEntity(String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    return json(response.getBody()).get("results").get(0);
  }

  private ResponseEntity<String> read(String uri) {
    return api(TENANT_A_KEY).get().uri(uri).retrieve().toEntity(String.class);
  }
}
