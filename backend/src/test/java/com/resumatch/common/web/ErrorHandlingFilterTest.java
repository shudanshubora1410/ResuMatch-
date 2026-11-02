package com.resumatch.common.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.resumatch.common.exception.ApiException;
import com.resumatch.testsupport.CapturedResponse;
import com.resumatch.testsupport.TestHttp;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ErrorHandlingFilterTest {

  private final ErrorHandlingFilter filter = new ErrorHandlingFilter();

  @Test
  @DisplayName("an ApiException becomes the standard error envelope with its own status")
  void apiExceptionBecomesEnvelope() throws Exception {
    CapturedResponse captured = new CapturedResponse();
    HttpServletRequest request = TestHttp.request("POST", "/api/v1/jobs");

    filter.doFilter(request, captured.mock(),
        TestHttp.throwingChain(ApiException.validation(Map.of("title", "Title is required"))));

    assertEquals(400, captured.status());
    assertEquals("application/json; charset=UTF-8", captured.contentType());
    String body = captured.body();
    assertTrue(body.contains("\"success\":false"), body);
    assertTrue(body.contains("\"status\":400"), body);
    assertTrue(body.contains("\"error\":\"VALIDATION_FAILED\""), body);
    assertTrue(body.contains("\"path\":\"/api/v1/jobs\""), body);
    assertTrue(body.contains("\"title\":\"Title is required\""), body);
    assertTrue(body.contains("\"timestamp\""), body);
  }

  @Test
  @DisplayName("an unexpected exception becomes a generic 500 without leaking internals")
  void unexpectedExceptionIsHidden() throws Exception {
    CapturedResponse captured = new CapturedResponse();
    HttpServletRequest request = TestHttp.request("GET", "/api/v1/jobs");

    filter.doFilter(request, captured.mock(),
        TestHttp.throwingChain(new IllegalStateException("SQLException: table jobs missing")));

    assertEquals(500, captured.status());
    String body = captured.body();
    assertTrue(body.contains("\"error\":\"INTERNAL_ERROR\""), body);
    assertFalse(body.contains("SQLException"), "internal details must not leak: " + body);
    assertFalse(body.contains("missing"), "internal details must not leak: " + body);
  }

  @Test
  @DisplayName("an unmapped /api path returns JSON 404 instead of Tomcat's HTML page")
  void unmappedApiPathBecomesJson404() throws Exception {
    CapturedResponse captured = new CapturedResponse();
    HttpServletRequest request = TestHttp.request("GET", "/api/v1/does-not-exist");

    filter.doFilter(request, captured.mock(), TestHttp.chainSetting404Status());

    assertEquals(404, captured.status());
    String body = captured.body();
    assertTrue(body.contains("\"success\":false"), body);
    assertTrue(body.contains("NOT_FOUND"), body);
  }

  @Test
  @DisplayName("a 404 on a static page is left alone, so HTML 404 pages still work")
  void staticNotFoundIsLeftAlone() throws Exception {
    CapturedResponse captured = new CapturedResponse();
    HttpServletRequest request = TestHttp.request("GET", "/missing-page.html");

    filter.doFilter(request, captured.mock(), TestHttp.chainSetting404Status());

    assertEquals(404, captured.status());
    assertEquals("", captured.body());
  }

  @Test
  @DisplayName("an IllegalArgumentException is treated as a 400, not a 500")
  void illegalArgumentBecomesBadRequest() throws Exception {
    CapturedResponse captured = new CapturedResponse();
    HttpServletRequest request = TestHttp.request("POST", "/api/v1/mail");

    filter.doFilter(request, captured.mock(),
        TestHttp.throwingChain(new IllegalArgumentException("Recipient address is required")));

    assertEquals(400, captured.status());
    assertTrue(captured.body().contains("\"error\":\"BAD_REQUEST\""), captured.body());
  }

  @Test
  @DisplayName("no error is written once the response has been committed")
  void committedResponseIsNotRewritten() throws Exception {
    CapturedResponse captured = new CapturedResponse();
    captured.markCommitted();
    HttpServletRequest request = TestHttp.request("GET", "/api/v1/jobs");

    filter.doFilter(request, captured.mock(),
        TestHttp.throwingChain(ApiException.notFound("Job not found.")));

    assertEquals(200, captured.status(), "status stays as it was");
    assertEquals("", captured.body());
  }
}
