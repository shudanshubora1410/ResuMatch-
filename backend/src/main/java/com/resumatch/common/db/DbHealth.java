package com.resumatch.common.db;

import java.time.Instant;

/**
 * Result of one database health probe.
 *
 * @param up        true when {@code SELECT 1} succeeded
 * @param latencyMs how long the probe took
 * @param checkedAt when the probe ran
 * @param detail    short failure description, or null when healthy (never contains credentials)
 */
public record DbHealth(boolean up, long latencyMs, Instant checkedAt, String detail) {

  public static DbHealth notCheckedYet() {
    return new DbHealth(false, 0L, null, "Health probe has not run yet");
  }
}
