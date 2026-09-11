package io.guestgraph.engine.integration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;

/** Spec 007 task T020: health answers without a credential, and nothing else is exposed. */
class HealthTest extends PostgresIntegrationTest {

  private RestClient bare() {
    return RestClient.builder()
        .baseUrl("http://localhost:" + port)
        .defaultStatusHandler(status -> true, (request, response) -> {})
        .build();
  }

  @Test
  @DisplayName("health answers 200 without an API key")
  void healthAnswersWithoutAKey() {
    assertThat(bare().get().uri("/actuator/health").retrieve().toBodilessEntity().getStatusCode())
        .isEqualTo(HttpStatus.OK);
  }

  @Test
  @DisplayName("no other actuator endpoint is exposed")
  void nothingElseIsExposed() {
    assertThat(bare().get().uri("/actuator/env").retrieve().toBodilessEntity().getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
  }
}
