package com.resumatch.testsupport;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Small factories for servlet mocks, so tests stay readable. */
public final class TestHttp {

  private TestHttp() {
    // Utility class.
  }

  public static HttpServletRequest request(String method, String uri) {
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getMethod()).thenReturn(method);
    when(request.getRequestURI()).thenReturn(uri);
    when(request.getContextPath()).thenReturn("");
    when(request.isSecure()).thenReturn(false);
    return request;
  }

  public static HttpServletRequest request(String method, String uri, String body) {
    return withBody(request(method, uri), body);
  }

  /** Attaches a request body, the way Tomcat's {@code getInputStream()} exposes it. */
  public static HttpServletRequest withBody(HttpServletRequest request, String body) {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    try {
      when(request.getInputStream()).thenReturn(new ServletInputStream() {
        private final ByteArrayInputStream source = new ByteArrayInputStream(bytes);

        @Override
        public boolean isFinished() {
          return source.available() == 0;
        }

        @Override
        public boolean isReady() {
          return true;
        }

        @Override
        public void setReadListener(ReadListener readListener) {
          // Not needed in tests.
        }

        @Override
        public int read() {
          return source.read();
        }

        @Override
        public int read(byte[] target, int offset, int length) {
          return source.read(target, offset, length);
        }
      });
    } catch (IOException ex) {
      throw new IllegalStateException("Could not stub the request body", ex);
    }
    return request;
  }

  /** A chain that does nothing; used when only the filter's own headers matter. */
  public static FilterChain noopChain() {
    return (ServletRequest request, ServletResponse response) -> {
      // Intentionally empty.
    };
  }

  /** A chain that throws, simulating a failing servlet or service. */
  public static FilterChain throwingChain(RuntimeException failure) {
    return (ServletRequest request, ServletResponse response) -> {
      throw failure;
    };
  }

  /** A chain that answers 404, simulating a path no servlet is mapped to. */
  public static FilterChain chainSetting404Status() {
    return (ServletRequest request, ServletResponse response) ->
        ((HttpServletResponse) response).setStatus(404);
  }
}
