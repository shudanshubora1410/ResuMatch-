package com.resumatch.common.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.resumatch.testsupport.CapturedResponse;
import com.resumatch.testsupport.TestHttp;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SecurityHeadersFilterTest {

  private final SecurityHeadersFilter filter = new SecurityHeadersFilter();

  @Test
  @DisplayName("every response gets the security headers, including a restrictive CSP")
  void headersAreAdded() throws Exception {
    CapturedResponse captured = new CapturedResponse();

    filter.doFilter(TestHttp.request("GET", "/api/v1/health"), captured.mock(),
        TestHttp.noopChain());

    String csp = captured.header("Content-Security-Policy");
    assertNotNull(csp);
    assertTrue(csp.contains("default-src 'self'"), csp);
    assertTrue(csp.contains("object-src 'none'"), csp);
    assertTrue(csp.contains("frame-ancestors 'none'"), csp);
    // These two origins are genuinely needed: Bootstrap/Chart.js and Google Identity Services.
    assertTrue(csp.contains("https://cdn.jsdelivr.net"), csp);
    assertTrue(csp.contains("https://accounts.google.com"), csp);

    assertEquals("nosniff", captured.header("X-Content-Type-Options"));
    assertEquals("DENY", captured.header("X-Frame-Options"));
    assertEquals("strict-origin-when-cross-origin", captured.header("Referrer-Policy"));
    assertNotNull(captured.header("Permissions-Policy"));
  }

  @Test
  @DisplayName("HSTS is only sent over HTTPS, so local HTTP development is not broken")
  void hstsOnlyOverHttps() throws Exception {
    CapturedResponse plain = new CapturedResponse();
    filter.doFilter(TestHttp.request("GET", "/"), plain.mock(), TestHttp.noopChain());
    assertFalse(plain.hasHeader("Strict-Transport-Security"));

    CapturedResponse secure = new CapturedResponse();
    HttpServletRequest secureRequest = mock(HttpServletRequest.class);
    when(secureRequest.isSecure()).thenReturn(true);
    filter.doFilter(secureRequest, secure.mock(), TestHttp.noopChain());

    assertTrue(secure.header("Strict-Transport-Security").contains("max-age=31536000"));
  }

  @Test
  @DisplayName("the CSP forbids framing, so clickjacking is blocked by the browser too")
  void framingIsBlocked() throws Exception {
    CapturedResponse captured = new CapturedResponse();

    filter.doFilter(TestHttp.request("GET", "/"), captured.mock(), TestHttp.noopChain());

    assertNull(captured.header("X-Powered-By"));
  }
}
