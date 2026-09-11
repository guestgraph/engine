package io.guestgraph.service;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Guards a service's surface with one bearer token from configuration: every path answers only to
 * {@code Authorization: Bearer <token>}, except the paths {@code service.bearer.open-paths} names,
 * as a YAML list or one comma-separated line, each an exact path or a prefix ending in a slash,
 * matched on the raw request URI so that a matrix parameter or a percent-encoded spelling of a
 * guarded path stays guarded. Present only when {@code service.bearer.token} is set; a service with
 * no such surface has no such filter. The scheme is case-insensitive (RFC 9110); the token is
 * compared in constant time. Vendored from guestgraph/service-conventions, never edited in a
 * service.
 */
@Component
@ConditionalOnProperty("service.bearer.token")
public class BearerTokenFilter extends OncePerRequestFilter {

  private final byte[] token;
  private final List<String> openPaths;

  public BearerTokenFilter(@Value("${service.bearer.token}") String token, Environment env) {
    this.token = token.getBytes(StandardCharsets.UTF_8);
    // A placeholder cannot see a YAML list (its keys are indexed); the binder reads both forms.
    this.openPaths =
        Binder.get(env)
            .bind("service.bearer.open-paths", Bindable.listOf(String.class))
            .orElse(List.of());
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI();
    for (String open : openPaths) {
      if (open.endsWith("/") ? path.startsWith(open) : path.equals(open)) {
        return true;
      }
    }
    return false;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String header = request.getHeader("Authorization");
    if (header != null && header.regionMatches(true, 0, "Bearer ", 0, 7)) {
      byte[] presented = header.substring(7).getBytes(StandardCharsets.UTF_8);
      if (MessageDigest.isEqual(token, presented)) {
        chain.doFilter(request, response);
        return;
      }
    }
    response.setHeader("WWW-Authenticate", "Bearer");
    Problems.write(
        response,
        Problems.of(
            HttpStatus.UNAUTHORIZED, "unauthorized", "Unauthorized", "A bearer token is required"));
  }
}
