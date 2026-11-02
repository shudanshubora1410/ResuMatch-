package com.resumatch.common.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.resumatch.testsupport.CapturedResponse;
import com.resumatch.testsupport.TestHttp;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ErrorPageServletTest {

  private final ErrorPageServlet servlet = new ErrorPageServlet();

  private static HttpServletRequest errorRequest(int status, String originalUri) {
    HttpServletRequest request = TestHttp.request("GET", "/error");
    when(request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE)).thenReturn(status);
    when(request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI)).thenReturn(originalUri);
    return request;
  }

  @Test
  @DisplayName("an unmapped API URL gets the JSON envelope, exactly like the filter's errors")
  void apiErrorsAreJson() throws Exception {
    CapturedResponse captured = new CapturedResponse();

    servlet.service(errorRequest(404, "/api/v1/unknown"), captured.mock());

    assertEquals(404, captured.status());
    assertTrue(captured.contentType().startsWith("application/json"), captured.contentType());
    String body = captured.body();
    assertTrue(body.contains("\"success\":false"), body);
    assertTrue(body.contains("\"error\":\"NOT_FOUND\""), body);
    assertTrue(body.contains("\"path\":\"/api/v1/unknown\""), body);
    assertTrue(body.contains("\"timestamp\""), body);
  }

  @Test
  @DisplayName("a broken page gets a branded HTML error page instead of JSON")
  void pageErrorsAreHtml() throws Exception {
    CapturedResponse captured = new CapturedResponse();

    servlet.service(errorRequest(404, "/nope.html"), captured.mock());

    assertEquals(404, captured.status());
    assertTrue(captured.contentType().startsWith("text/html"), captured.contentType());
    String body = captured.body();
    assertTrue(body.contains("Page not found"), body);
    assertTrue(body.contains("Built by Shudanshu Bora"), body);
    assertTrue(body.contains("support.resumatch@gmail.com"), body);
  }

  @Test
  @DisplayName("a 500 never leaks the exception message to the browser")
  void serverErrorsAreGeneric() throws Exception {
    CapturedResponse captured = new CapturedResponse();
    HttpServletRequest request = errorRequest(500, "/api/v1/jobs");
    // Tomcat exposes the exception here; the page must ignore it.
    when(request.getAttribute(RequestDispatcher.ERROR_EXCEPTION))
        .thenReturn(new IllegalStateException("SQLException: table jobs is missing"));

    servlet.service(request, captured.mock());

    assertEquals(500, captured.status());
    assertFalse(captured.body().contains("SQLException"), captured.body());
    assertTrue(captured.body().contains("\"error\":\"INTERNAL_ERROR\""), captured.body());
  }

  @Test
  @DisplayName("a missing status attribute defaults to 500 instead of throwing")
  void missingStatusDefaultsTo500() throws Exception {
    CapturedResponse captured = new CapturedResponse();
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getRequestURI()).thenReturn("/error");

    servlet.service(request, captured.mock());

    assertEquals(500, captured.status());
  }
}
