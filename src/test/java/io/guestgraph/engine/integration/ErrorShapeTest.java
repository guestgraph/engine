package io.guestgraph.engine.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

/** Spec 008 task T011: every refusal, from every origin, has the family's shape. */
class ErrorShapeTest extends PostgresIntegrationTest {

  static final String BASE = "https://guestgraph.io/problems/#";

  @Test
  @DisplayName("a filter's refusal is a problem with the family's type")
  void filter() {
    ResponseEntity<String> r =
        api(null)
            .get()
            .uri("/api/v1/guests/" + UUID.randomUUID())
            .retrieve()
            .toEntity(String.class);
    assertShape(r, 401, "unauthorized");
  }

  @Test
  @DisplayName("a controller's not-found is a problem with the family's type")
  void controller() {
    ResponseEntity<String> r =
        api(TENANT_A_KEY)
            .get()
            .uri("/api/v1/guests/" + UUID.randomUUID())
            .retrieve()
            .toEntity(String.class);
    assertShape(r, 404, "not-found");
  }

  @Test
  @DisplayName("a conflict is a problem with the family's type")
  void conflict() {
    String body = "{\"code\":\"shape-test\",\"name\":\"Shape test\"}";
    api(TENANT_A_KEY)
        .post()
        .uri("/api/v1/source-systems")
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .retrieve()
        .toEntity(String.class);
    ResponseEntity<String> r =
        api(TENANT_A_KEY)
            .post()
            .uri("/api/v1/source-systems")
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .toEntity(String.class);
    assertShape(r, 409, "conflict");
  }

  @Test
  @DisplayName("the framework's own refusal is a problem without the family's type")
  void framework() {
    ResponseEntity<String> r =
        api(TENANT_A_KEY)
            .post()
            .uri("/api/v1/records")
            .contentType(MediaType.APPLICATION_JSON)
            .body("not json")
            .retrieve()
            .toEntity(String.class);
    assertThat(r.getStatusCode().value()).isEqualTo(400);
    assertThat(r.getHeaders().getContentType().toString()).startsWith("application/problem+json");
    JsonNode problem = json(r.getBody());
    assertThat(problem.get("status").asInt()).isEqualTo(400);
    assertThat(problem.has("type") ? problem.get("type").asString() : "").doesNotStartWith(BASE);
  }

  @Test
  @DisplayName("what nobody foresaw is an internal-error problem that says nothing of the cause")
  void unforeseen() {
    ResponseEntity<String> r = api(null).get().uri("/test/boom").retrieve().toEntity(String.class);
    assertShape(r, 500, "internal-error");
    assertThat(json(r.getBody()).get("detail").asString())
        .isEqualTo("An unexpected error occurred");
    assertThat(r.getBody()).doesNotContain("the cause");
  }

  private void assertShape(ResponseEntity<String> r, int status, String slug) {
    assertThat(r.getStatusCode().value()).isEqualTo(status);
    assertThat(r.getHeaders().getContentType().toString()).startsWith("application/problem+json");
    JsonNode problem = json(r.getBody());
    assertThat(problem.path("type").asString()).isEqualTo(BASE + slug);
    assertThat(problem.get("title").asString()).isNotBlank();
    assertThat(problem.get("status").asInt()).isEqualTo(status);
    assertThat(problem.get("detail").asString()).isNotBlank();
  }
}
