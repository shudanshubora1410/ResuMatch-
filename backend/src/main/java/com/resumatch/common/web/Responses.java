package com.resumatch.common.web;

import com.resumatch.common.exception.ApiException;
import com.resumatch.common.json.Json;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Writes JSON responses. The only place that touches {@code response.getWriter()} for the API. */
public final class Responses {

  private static final Logger log = LoggerFactory.getLogger(Responses.class);
  private static final String JSON_CONTENT_TYPE = "application/json; charset=UTF-8";

  private Responses() {
    // Utility class.
  }

  public static void writeJson(HttpServletResponse response, int status, Object body) {
    try {
      response.setStatus(status);
      // no-store: API payloads (scores, applications) must never be cached by a proxy.
      response.setHeader("Cache-Control", "no-store");
      response.setContentType(JSON_CONTENT_TYPE);
      response.getWriter().write(Json.toJson(body));
      // Flushing commits the response. Without it Tomcat's ErrorReportValve would replace a
      // JSON 404/405 with its own HTML error page after the filter chain returns.
      response.getWriter().flush();
    } catch (IOException ex) {
      // The client hung up; nothing useful can be sent, so just note it.
      log.debug("Could not write JSON response: {}", ex.getMessage());
    }
  }

  public static void writeError(HttpServletResponse response, HttpServletRequest request,
                                ApiException exception) {
    writeJson(response, exception.getStatus(), ErrorResponse.of(
        exception.getStatus(),
        exception.getError(),
        exception.getMessage(),
        requestPath(request),
        exception.getFieldErrors()));
  }

  /** For unexpected failures: the client gets a generic message, the log gets the details. */
  public static void writeUnexpectedError(HttpServletResponse response, HttpServletRequest request) {
    writeJson(response, 500, ErrorResponse.of(
        500,
        "INTERNAL_ERROR",
        "Something went wrong on our side. Please try again.",
        requestPath(request),
        Map.of()));
  }

  /** Path only (no query string): query strings can contain tokens we must not echo back. */
  private static String requestPath(HttpServletRequest request) {
    return request.getRequestURI();
  }
}
