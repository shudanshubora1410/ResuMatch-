package com.resumatch.common.db;

import java.time.Instant;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Applies the Flyway migrations, retrying with exponential backoff.
 *
 * <p>Why a background thread: on Render's free plan the app and the Aiven database wake up at
 * different times. Crashing when the DB is still asleep would turn into a restart loop, so we
 * boot, answer {@code /health} as "degraded", and keep retrying until the DB appears.</p>
 */
public final class DatabaseBootstrap implements Runnable {

  private static final Logger log = LoggerFactory.getLogger(DatabaseBootstrap.class);

  private static final int MAX_ATTEMPTS = 8;
  private static final long BASE_DELAY_SECONDS = 3;
  private static final long MAX_DELAY_SECONDS = 45;

  private final Database database;
  private volatile MigrationState state = MigrationState.pending();

  public DatabaseBootstrap(Database database) {
    this.database = database;
  }

  @Override
  public void run() {
    for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
      state = new MigrationState(MigrationState.Status.RUNNING, attempt, state.lastError(), null);
      try {
        int applied = migrate();
        log.info("Flyway finished on attempt {} ({} migration(s) applied)", attempt, applied);
        state = new MigrationState(MigrationState.Status.SUCCEEDED, attempt, null, Instant.now());
        return;
      } catch (RuntimeException ex) {
        String shortError = describe(ex);
        state = new MigrationState(MigrationState.Status.RUNNING, attempt, shortError, null);
        if (attempt == MAX_ATTEMPTS) {
          log.error("Flyway failed after {} attempts: {}", MAX_ATTEMPTS, shortError);
          state = new MigrationState(MigrationState.Status.FAILED, attempt, shortError, null);
          return;
        }
        long delay = Math.min(MAX_DELAY_SECONDS, BASE_DELAY_SECONDS * (1L << (attempt - 1)));
        log.warn("Flyway attempt {} failed ({}); retrying in {}s", attempt, shortError, delay);
        if (!sleepSeconds(delay)) {
          log.warn("Migration retry interrupted; stopping");
          return;
        }
      }
    }
  }

  /** Extracted so tests can verify the configured locations without touching a database. */
  static Flyway buildFlyway(Database database) {
    return Flyway.configure()
        .dataSource(database.getDataSource())
        // Migrations live on the classpath so the same WAR works locally, in Docker and on Render.
        .locations("classpath:db/migration")
        .baselineOnMigrate(true)
        .load();
  }

  private int migrate() {
    return buildFlyway(database).migrate().migrationsExecuted;
  }

  private static String describe(RuntimeException ex) {
    // Flyway exceptions can be long; keep the log and /health readable.
    String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
    String firstLine = message.split("\\R", 2)[0];
    return firstLine.length() <= 200 ? firstLine : firstLine.substring(0, 200) + "...";
  }

  private static boolean sleepSeconds(long seconds) {
    try {
      Thread.sleep(seconds * 1000L);
      return true;
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      return false;
    }
  }

  public MigrationState state() {
    return state;
  }
}
