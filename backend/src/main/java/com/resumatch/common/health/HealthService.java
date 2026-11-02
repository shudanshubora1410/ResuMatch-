package com.resumatch.common.health;

import com.resumatch.common.db.DbHealth;
import com.resumatch.common.db.MigrationState;
import com.resumatch.common.startup.AppServices;
import java.time.Duration;
import java.time.Instant;

/**
 * Builds the health snapshot from cached state only.
 *
 * <p>No database call happens here: the snapshot is assembled from the health monitor's last
 * probe and the Flyway state, so the endpoint stays fast even when the database is unreachable.</p>
 */
public final class HealthService {

  private HealthService() {
    // Utility class.
  }

  public static HealthSnapshot snapshot(AppServices services) {
    DbHealth dbHealth = services.dbHealthMonitor().latest();
    MigrationState migrations = services.databaseBootstrap().state();

    ComponentStatus databaseStatus = new ComponentStatus(
        dbHealth.up() ? "UP" : "DOWN",
        dbHealth.latencyMs(),
        dbHealth.checkedAt(),
        dbHealth.detail());

    MigrationStatusView migrationStatus = new MigrationStatusView(
        migrations.status().name(),
        migrations.lastError());

    // The app is only fully healthy when both the database and the schema are ready.
    String status = (dbHealth.up() && migrations.isSucceeded())
        ? HealthSnapshot.UP
        : HealthSnapshot.DEGRADED;

    return new HealthSnapshot(
        status,
        services.version(),
        services.environment(),
        Instant.now(),
        Duration.between(services.startedAt(), Instant.now()).getSeconds(),
        databaseStatus,
        migrationStatus,
        services.fileStorage().name(),
        services.mailSender().provider());
  }
}
