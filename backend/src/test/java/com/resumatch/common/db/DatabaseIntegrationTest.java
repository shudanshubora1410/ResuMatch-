package com.resumatch.common.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.resumatch.common.config.AppConfig;
import com.resumatch.common.config.ConfigLoader;
import com.resumatch.common.config.MissingConfigException;
import com.resumatch.testsupport.TestDatabase;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Runs against a real MySQL server (your local {@code resumatch_test} schema, or the service
 * container in GitHub Actions). No Docker required.
 */
class DatabaseIntegrationTest {

  @Test
  @DisplayName("the pool connects and SELECT 1 succeeds")
  void connectsAndPings() {
    TestDatabase.assumeAvailable();

    try (Database database = Database.create(TestDatabase.config())) {
      DbHealth health = database.ping();

      assertTrue(health.up(), "expected the test database to be reachable: " + health.detail());
      assertEquals(null, health.detail());
      // The masked URL must never contain the password.
      assertFalse(database.getMaskedJdbcUrl().contains(
          TestDatabase.config().get(AppConfig.DB_PASSWORD, "never-matches-empty")),
          database.getMaskedJdbcUrl());
    }
  }

  @Test
  @DisplayName("Flyway creates the schema, and a second run is a no-op")
  void migrationsAreIdempotent() {
    TestDatabase.assumeAvailable();
    Database database = Database.create(TestDatabase.config());
    try {
      DatabaseBootstrap bootstrap = new DatabaseBootstrap(database);
      bootstrap.run();

      assertEquals(MigrationState.Status.SUCCEEDED, bootstrap.state().status(),
          String.valueOf(bootstrap.state().lastError()));

      // Running again must not try to re-apply V1 (Flyway keeps a schema history table).
      DatabaseBootstrap second = new DatabaseBootstrap(database);
      second.run();
      assertEquals(MigrationState.Status.SUCCEEDED, second.state().status());

      try (Connection connection = database.getConnection();
           Statement statement = connection.createStatement();
           ResultSet rows = statement.executeQuery(
               "SELECT meta_value FROM app_metadata WHERE meta_key = 'app_name'")) {
        assertTrue(rows.next(), "V1__baseline.sql should have seeded app_metadata");
        assertEquals("ResuMatch", rows.getString(1));
      }
    } catch (Exception ex) {
      throw new AssertionError("Database assertion failed", ex);
    } finally {
      database.close();
    }
  }

  @Test
  @DisplayName("the health monitor caches a snapshot without blocking callers")
  void healthMonitorCachesSnapshot() {
    TestDatabase.assumeAvailable();

    try (Database database = Database.create(TestDatabase.config())) {
      DbHealthMonitor monitor = new DbHealthMonitor(database);
      try {
        monitor.start();
        // The first probe is scheduled immediately; give it a moment to land.
        long deadline = System.currentTimeMillis() + 15_000;
        while (!monitor.latest().up() && System.currentTimeMillis() < deadline) {
          Thread.sleep(100);
        }
        assertTrue(monitor.latest().up(), "expected an UP snapshot");
        assertTrue(monitor.latest().checkedAt() != null);
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
        throw new AssertionError("Interrupted while waiting for the health probe", ex);
      } finally {
        monitor.stop();
      }
    }
  }

  @Test
  @DisplayName("a missing DB_URL stops startup with a clear message")
  void missingUrlFailsFast() {
    AppConfig noUrl = ConfigLoader.fromSources(Map.of(AppConfig.DB_USER, "someone"), Map.of());

    assertThrows(MissingConfigException.class, () -> Database.create(noUrl));
  }
}
