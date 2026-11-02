package com.resumatch.common.web;

/**
 * The one success shape used by every endpoint: {@code {"success": true, "data": ...}}.
 *
 * <p>Mirrors {@link ErrorResponse} so the frontend can always check {@code success} first.</p>
 */
public record ApiSuccess<T>(boolean success, T data) {

  public static <T> ApiSuccess<T> of(T data) {
    return new ApiSuccess<>(true, data);
  }
}
