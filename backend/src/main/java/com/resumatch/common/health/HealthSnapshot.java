package com.resumatch.common.health;

import java.time.Instant;

/**
 * Body of {@code GET /api/v1/health}.
 *
 * <p>{@code status} is {@code UP} only when the database answers and the migrations are applied;
 * otherwise {@code DEGRADED}. The HTTP status stays 200 in both cases on purpose: Render restarts
 * a service whose health check fails, and a sleep-then-wake database must not cause a restart
 * loop. Uptime monitors can still alert on {@code status}.</p>
 */
public record HealthSnapshot(
    String status,
    String version,
    String environment,
    Instant time,
    long uptimeSeconds,
    ComponentStatus database,
    MigrationStatusView migrations,
    String storage,
    String mailProvider) {

  public static final String UP = "UP";
  public static final String DEGRADED = "DEGRADED";
}
