package com.resumatch.common.web;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Adds the security headers required on every response.
 *
 * <p>The Content-Security-Policy is written for this project's real dependencies: Bootstrap and
 * Chart.js come from jsDelivr, Google Identity Services from accounts.google.com, and Chart.js
 * sets element styles inline. Everything else is locked to our own origin.</p>
 */
public final class SecurityHeadersFilter implements Filter {

  private static final String CDN = "https://cdn.jsdelivr.net";
  private static final String GOOGLE = "https://accounts.google.com";

  private static final String CONTENT_SECURITY_POLICY = String.join("; ",
      "default-src 'self'",
      "script-src 'self' " + CDN + " " + GOOGLE,
      // 'unsafe-inline' is limited to styles: Bootstrap utilities and Chart.js set inline styles.
      "style-src 'self' 'unsafe-inline' " + CDN,
      "img-src 'self' data:",
      "font-src 'self' data: " + CDN,
      "connect-src 'self' " + GOOGLE,
      "frame-src " + GOOGLE,
      "object-src 'none'",
      "base-uri 'self'",
      "form-action 'self'",
      "frame-ancestors 'none'");

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    if (response instanceof HttpServletResponse httpResponse) {
      httpResponse.setHeader("Content-Security-Policy", CONTENT_SECURITY_POLICY);
      // Stop the browser from guessing that a text file is really JavaScript.
      httpResponse.setHeader("X-Content-Type-Options", "nosniff");
      // Clickjacking protection (also covered by frame-ancestors, kept for older browsers).
      httpResponse.setHeader("X-Frame-Options", "DENY");
      httpResponse.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
      httpResponse.setHeader("Permissions-Policy",
          "geolocation=(), microphone=(), camera=(), payment=()");
      // HSTS only makes sense over HTTPS; behind Render's proxy isSecure() is true thanks to
      // the RemoteIpValve configured in META-INF/context.xml.
      if (request instanceof HttpServletRequest httpRequest && httpRequest.isSecure()) {
        httpResponse.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
      }
    }
    chain.doFilter(request, response);
  }
}
