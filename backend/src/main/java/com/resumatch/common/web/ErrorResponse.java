package com.resumatch.common.web;

import java.time.Instant;
import java.util.Map;

/**
 * The one error shape used by every endpoint:
 * {@code {success:false, timestamp, status, error, message, path, fieldErrors}}.
 *
 * @param timestamp ISO-8601 instant, so the frontend can log or display it
 * @param error     short machine-readable code, e.g. VALIDATION_FAILED
 * @param message   safe human-readable message (never a stack trace or SQL text)
 * @param fieldErrors per-field validation messages, empty when not a validation error
 */
public record ErrorResponse(
    boolean success,
    Instant timestamp,
    int status,
    String error,
    String message,
    String path,
    Map<String, String> fieldErrors) {

  public static ErrorResponse of(int status, String error, String message, String path,
                                 Map<String, String> fieldErrors) {
    return new ErrorResponse(false, Instant.now(), status, error, message, path,
        fieldErrors == null ? Map.of() : fieldErrors);
  }
}
