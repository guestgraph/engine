package io.guestgraph.service;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import tools.jackson.databind.json.JsonMapper;

/**
 * The one place a guestgraph service's refusal takes its shape (RFC 9457): a type under the
 * family's problems page, a title, the status and a detail, then any named members a specific
 * problem adds. A filter writes one through {@link #write}; a controller throws a {@link
 * ServiceException}, whose problem is built the same way. Vendored from
 * guestgraph/service-conventions, never edited in a service.
 */
public final class Problems {

  /** Every type is a section of the family's problems page. */
  public static final String BASE = "https://guestgraph.io/problems/#";

  private static final JsonMapper JSON = JsonMapper.builder().build();

  private Problems() {}

  public static ProblemDetail of(HttpStatus status, String slug, String title, String detail) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setType(URI.create(BASE + slug));
    problem.setTitle(title);
    return problem;
  }

  /**
   * Writes the problem as the response: its status, {@code application/problem+json}, the members.
   */
  public static void write(HttpServletResponse response, ProblemDetail problem) throws IOException {
    response.setStatus(problem.getStatus());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    response.setCharacterEncoding("UTF-8");
    response.getWriter().write(JSON.writeValueAsString(members(problem)));
  }

  /** The members in RFC 9457's order, the named ones last; nothing null. */
  static Map<String, Object> members(ProblemDetail problem) {
    Map<String, Object> members = new LinkedHashMap<>();
    members.put("type", problem.getType().toString());
    if (problem.getTitle() != null) {
      members.put("title", problem.getTitle());
    }
    members.put("status", problem.getStatus());
    if (problem.getDetail() != null) {
      members.put("detail", problem.getDetail());
    }
    if (problem.getInstance() != null) {
      members.put("instance", problem.getInstance().toString());
    }
    if (problem.getProperties() != null) {
      problem.getProperties().forEach(members::putIfAbsent);
    }
    return members;
  }
}
