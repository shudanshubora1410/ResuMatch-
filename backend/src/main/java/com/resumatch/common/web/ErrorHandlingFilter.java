package com.resumatch.common.web;

import com.resumatch.common.exception.ApiException;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The single place where API errors become JSON.
 *
 * <p>Covers three cases:</p>
 * <ol>
 *   <li>An {@link ApiException} thrown by a servlet or service - rendered with its own status.</li>
 *   <li>Any unexpected exception - logged with full details, answered with a generic 500 so no
 *       stack trace, SQL text or class name ever reaches the browser.</li>
 *   <li>A request to an unmapped {@code /api/...} path - Tomcat would return its HTML 404 page,
 *       so we rewrite it into the standard error envelope.</li>
 * </ol>
 */
public final class ErrorHandlingFilter implements Filter {

  private static final Logger log = LoggerFactory.getLogger(ErrorHandlingFilter.class);
  private static final String API_PREFIX = "/api/";

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    if (!(request instanceof HttpServletRequest httpRequest)
        || !(response instanceof HttpServletResponse httpResponse)) {
      chain.doFilter(request, response);
      return;
    }

    try {
      chain.doFilter(request, response);
      convertContainerNotFound(httpRequest, httpResponse);
    } catch (ApiException ex) {
      if (httpResponse.isCommitted()) {
        log.debug("Cannot render error, response already committed: {}", ex.getMessage());
        return;
      }
      // 4xx are normal user mistakes: log at debug/info, not as errors.
      if (ex.getStatus() >= 500) {
        log.warn("{} on {} {}", ex.getError(), httpRequest.getMethod(),
            httpRequest.getRequestURI(), ex);
      } else {
        log.debug("{} on {} {}: {}", ex.getError(), httpRequest.getMethod(),
            httpRequest.getRequestURI(), ex.getMessage());
      }
      Responses.writeError(httpResponse, httpRequest, ex);
    } catch (IllegalArgumentException ex) {
      // Defensive mapping: our own code uses IllegalArgumentException for bad input.
      log.debug("Bad input on {} {}: {}", httpRequest.getMethod(), httpRequest.getRequestURI(),
          ex.getMessage());
      if (!httpResponse.isCommitted()) {
        Responses.writeError(httpResponse, httpRequest,
            ApiException.badRequest("The request could not be processed."));
      }
    } catch (Exception ex) {
      log.error("Unhandled error on {} {}", httpRequest.getMethod(), httpRequest.getRequestURI(), ex);
      if (!httpResponse.isCommitted()) {
        Responses.writeUnexpectedError(httpResponse, httpRequest);
      }
    }
  }

  /**
   * Tomcat answers unmapped paths with its own HTML page. For API URLs the frontend expects JSON,
   * so a container 404 is turned into the standard envelope.
   */
  private void convertContainerNotFound(HttpServletRequest request, HttpServletResponse response) {
    String path = request.getRequestURI();
    boolean apiRequest = path != null && path.contains(API_PREFIX);
    if (apiRequest && !response.isCommitted() && response.getStatus() == 404) {
      Responses.writeJson(response, 404, ErrorResponse.of(
          404,
          "NOT_FOUND",
          "No API endpoint matches " + request.getMethod() + " " + path,
          path,
          Map.of()));
    }
  }
}
