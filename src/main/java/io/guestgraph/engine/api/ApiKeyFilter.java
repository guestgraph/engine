package io.guestgraph.engine.api;

import io.guestgraph.engine.domain.Credential;
import io.guestgraph.engine.persistence.TenantStore;
import io.guestgraph.service.Problems;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates every /api request with a per-tenant API key (header X-API-Key, SHA-256 hash
 * lookup) and binds the tenant to the request. 401 responses are RFC 9457 problem details.
 */
@Component
public class ApiKeyFilter extends OncePerRequestFilter {

  public static final String API_KEY_HEADER = "X-API-Key";

  private final TenantStore tenantStore;

  public ApiKeyFilter(TenantStore tenantStore) {
    this.tenantStore = tenantStore;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !request.getRequestURI().startsWith("/api/");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String apiKey = request.getHeader(API_KEY_HEADER);
    if (apiKey == null || apiKey.isBlank()) {
      unauthorized(response, "Missing " + API_KEY_HEADER + " header");
      return;
    }
    Optional<Credential> credential = tenantStore.findCredentialByApiKeyHash(Sha256.hex(apiKey));
    if (credential.isEmpty()) {
      unauthorized(response, "Unknown or revoked API key");
      return;
    }
    TenantContext.set(credential.get().tenant());
    try {
      ActorResolver.set(
          ActorResolver.resolve(
              credential.get().actorType(),
              credential.get().actorName(),
              request.getHeader(ActorResolver.ACTOR_TYPE_HEADER),
              request.getHeader(ActorResolver.ACTOR_ID_HEADER)));
    } catch (InvalidActorClaimException e) {
      TenantContext.clear();
      invalidActorClaim(response, e.getMessage());
      return;
    }
    try {
      chain.doFilter(request, response);
    } finally {
      ActorResolver.clear();
      TenantContext.clear();
    }
  }

  private void invalidActorClaim(HttpServletResponse response, String detail) throws IOException {
    Problems.write(
        response,
        Problems.of(HttpStatus.BAD_REQUEST, "invalid-actor-claim", "Invalid actor claim", detail));
  }

  private void unauthorized(HttpServletResponse response, String detail) throws IOException {
    Problems.write(
        response, Problems.of(HttpStatus.UNAUTHORIZED, "unauthorized", "Unauthorized", detail));
  }
}
