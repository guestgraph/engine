package io.guestgraph.service;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Caps the size of a request body on every path: what a service stores is never larger than it
 * meant to store. A declared length over the cap is refused from the header alone; a body without a
 * declared length, the chunked one a header-only check misses, is buffered up to the cap plus one
 * byte and refused when it overruns. Both answer the family's payload-too-large problem. Memory is
 * bounded by the cap, which the JSON layer would buffer anyway. Vendored from
 * guestgraph/service-conventions, never edited in a service.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestSizeLimitFilter extends OncePerRequestFilter {

  private final long maxRequestBytes;

  public RequestSizeLimitFilter(
      @Value("${service.max-request-bytes:1048576}") long maxRequestBytes) {
    this.maxRequestBytes = maxRequestBytes;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    long declared = request.getContentLengthLong();
    if (declared > maxRequestBytes) {
      refuse(response);
      return;
    }
    if (declared >= 0) {
      // Declared and within the cap; the container enforces the declared length.
      chain.doFilter(request, response);
      return;
    }
    byte[] body = readAtMost(request.getInputStream(), maxRequestBytes);
    if (body == null) {
      refuse(response);
      return;
    }
    chain.doFilter(new CachedBodyRequest(request, body), response);
  }

  private void refuse(HttpServletResponse response) throws IOException {
    Problems.write(
        response,
        Problems.of(
            HttpStatus.CONTENT_TOO_LARGE,
            "payload-too-large",
            "Payload too large",
            "The request body may not exceed " + maxRequestBytes + " bytes"));
  }

  /** Reads the full stream, or returns null as soon as it exceeds {@code max} bytes. */
  private static byte[] readAtMost(InputStream in, long max) throws IOException {
    ByteArrayOutputStream buffer = new ByteArrayOutputStream(8192);
    byte[] chunk = new byte[8192];
    long total = 0;
    int n;
    while ((n = in.read(chunk)) != -1) {
      total += n;
      if (total > max) {
        return null;
      }
      buffer.write(chunk, 0, n);
    }
    return buffer.toByteArray();
  }

  private static final class CachedBodyRequest extends HttpServletRequestWrapper {

    private final byte[] body;

    private CachedBodyRequest(HttpServletRequest request, byte[] body) {
      super(request);
      this.body = body;
    }

    @Override
    public int getContentLength() {
      return body.length;
    }

    @Override
    public long getContentLengthLong() {
      return body.length;
    }

    @Override
    public ServletInputStream getInputStream() {
      ByteArrayInputStream source = new ByteArrayInputStream(body);
      return new ServletInputStream() {
        @Override
        public int read() {
          return source.read();
        }

        @Override
        public int read(byte[] buffer, int offset, int length) {
          return source.read(buffer, offset, length);
        }

        @Override
        public boolean isFinished() {
          return source.available() == 0;
        }

        @Override
        public boolean isReady() {
          return true;
        }

        @Override
        public void setReadListener(ReadListener listener) {}
      };
    }
  }
}
