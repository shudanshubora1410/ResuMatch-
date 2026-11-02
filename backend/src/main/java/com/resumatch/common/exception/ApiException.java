package com.resumatch.common.exception;

import java.util.Map;

/**
 * An error the application raises on purpose, carrying the HTTP status we want to return.
 *
 * <p>Why a single class with factory methods: services can throw a business error from anywhere
 * and the filter turns it into the standard error envelope, without services importing servlet
 * classes. Field errors are included for validation problems.</p>
 */
// Field values are plain strings/maps, so the serial warning is not worth a transient field.
@SuppressWarnings("serial")
public class ApiException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final int status;
  private final String error;
  private final Map<String, String> fieldErrors;

  public ApiException(int status, String error, String message, Map<String, String> fieldErrors) {
    super(message);
    this.status = status;
    this.error = error;
    this.fieldErrors = fieldErrors == null ? Map.of() : Map.copyOf(fieldErrors);
  }

  public int getStatus() {
    return status;
  }

  /** Short machine-readable code, e.g. NOT_FOUND or VALIDATION_FAILED. */
  public String getError() {
    return error;
  }

  public Map<String, String> getFieldErrors() {
    return fieldErrors;
  }

  public static ApiException badRequest(String message) {
    return new ApiException(400, "BAD_REQUEST", message, Map.of());
  }

  public static ApiException validation(Map<String, String> fieldErrors) {
    return new ApiException(400, "VALIDATION_FAILED", "Please correct the highlighted fields.", fieldErrors);
  }

  public static ApiException unauthorized(String message) {
    return new ApiException(401, "UNAUTHORIZED", message, Map.of());
  }

  public static ApiException forbidden(String message) {
    return new ApiException(403, "FORBIDDEN", message, Map.of());
  }

  public static ApiException notFound(String message) {
    return new ApiException(404, "NOT_FOUND", message, Map.of());
  }

  public static ApiException methodNotAllowed(String message) {
    return new ApiException(405, "METHOD_NOT_ALLOWED", message, Map.of());
  }

  public static ApiException conflict(String message) {
    return new ApiException(409, "CONFLICT", message, Map.of());
  }

  public static ApiException payloadTooLarge(String message) {
    return new ApiException(413, "PAYLOAD_TOO_LARGE", message, Map.of());
  }

  public static ApiException rateLimited(String message) {
    return new ApiException(429, "RATE_LIMITED", message, Map.of());
  }

  /** Used when the database is asleep/unreachable (Aiven free plan powers off when idle). */
  public static ApiException serviceUnavailable(String message) {
    return new ApiException(503, "SERVICE_UNAVAILABLE", message, Map.of());
  }

  public static ApiException unsupportedMediaType(String message) {
    return new ApiException(415, "UNSUPPORTED_MEDIA_TYPE", message, Map.of());
  }
}
