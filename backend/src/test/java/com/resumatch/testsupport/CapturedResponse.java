package com.resumatch.testsupport;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletResponse;
import org.mockito.Mockito;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A lightweight {@link HttpServletResponse} stand-in that records status, headers, content type
 * and body.
 *
 * <p>Written by hand instead of mocking every method in each test, so each test only states what
 * matters for the behaviour under test.</p>
 */
public final class CapturedResponse {

  private final ByteArrayOutputStream body = new ByteArrayOutputStream();
  private final PrintWriter writer =
      new PrintWriter(new OutputStreamWriter(body, StandardCharsets.UTF_8), true);
  private final Map<String, String> headers = new LinkedHashMap<>();
  private final HttpServletResponse response;

  private int status = 200;
  private String contentType;
  private boolean committed;

  public CapturedResponse() {
    // Fully qualified: this class has its own mock() accessor, which would shadow the import.
    this.response = Mockito.mock(HttpServletResponse.class);
    try {
      when(response.getWriter()).thenReturn(writer);
      when(response.getOutputStream()).thenReturn(new ServletOutputStream() {
        @Override
        public boolean isReady() {
          return true;
        }

        @Override
        public void setWriteListener(WriteListener writeListener) {
          // Not needed in tests.
        }

        @Override
        public void write(int value) {
          body.write(value);
        }
      });
    } catch (IOException ex) {
      throw new IllegalStateException("Could not stub the response", ex);
    }
    doAnswer(invocation -> {
      status = invocation.getArgument(0);
      return null;
    }).when(response).setStatus(anyInt());
    doAnswer(invocation -> {
      headers.put(invocation.getArgument(0), invocation.getArgument(1));
      return null;
    }).when(response).setHeader(anyString(), anyString());
    doAnswer(invocation -> {
      contentType = invocation.getArgument(0);
      return null;
    }).when(response).setContentType(anyString());
    when(response.isCommitted()).thenAnswer(invocation -> committed);
    when(response.getStatus()).thenAnswer(invocation -> status);
  }

  public HttpServletResponse mock() {
    return response;
  }

  public int status() {
    return status;
  }

  public String contentType() {
    return contentType;
  }

  public String header(String name) {
    return headers.get(name);
  }

  public boolean hasHeader(String name) {
    return headers.containsKey(name);
  }

  public String body() {
    writer.flush();
    return body.toString(StandardCharsets.UTF_8);
  }

  /** Simulates a response that already started streaming, so it can no longer be changed. */
  public void markCommitted() {
    this.committed = true;
  }
}
