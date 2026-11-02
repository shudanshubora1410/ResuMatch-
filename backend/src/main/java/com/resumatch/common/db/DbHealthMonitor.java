package com.resumatch.common.db;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Keeps an up-to-date copy of the database's health in memory.
 *
 * <p>Why: {@code /api/v1/health} must answer in milliseconds even when the database is asleep
 * (Aiven powers free instances off after idle time). Probing the DB directly inside the request
 * would block for the connection timeout and make Render's health check time out.</p>
 */
public final class DbHealthMonitor {

  private static final Logger log = LoggerFactory.getLogger(DbHealthMonitor.class);
  private static final Duration INTERVAL = Duration.ofSeconds(20);

  private final Database database;
  private final ScheduledExecutorService scheduler;
  private volatile DbHealth latest = DbHealth.notCheckedYet();

  public DbHealthMonitor(Database database) {
    this.database = database;
    this.scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
      Thread thread = new Thread(runnable, "db-health-monitor");
      thread.setDaemon(true); // must never keep Tomcat from shutting down
      return thread;
    });
  }

  public void start() {
    scheduler.scheduleWithFixedDelay(this::refresh, 0, INTERVAL.getSeconds(), TimeUnit.SECONDS);
    log.info("Database health monitor started (every {}s)", INTERVAL.getSeconds());
  }

  private void refresh() {
    try {
      latest = database.ping();
    } catch (RuntimeException ex) {
      // A monitor must never kill its own scheduled task.
      log.warn("Unexpected error while checking database health", ex);
    }
  }

  /** Last known state; never null, never blocks. */
  public DbHealth latest() {
    return latest;
  }

  public void stop() {
    scheduler.shutdownNow();
  }
}
