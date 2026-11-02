package com.resumatch.common.health;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.resumatch.common.db.DbHealth;
import com.resumatch.common.db.MigrationState;
import com.resumatch.common.startup.AppServices;
import com.resumatch.testsupport.TestServices;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class HealthServiceTest {

  @Test
  @DisplayName("UP only when the database answers and the migrations are applied")
  void healthyWhenDbAndMigrationsAreReady() {
    AppServices services = TestServices.healthy();

    HealthSnapshot snapshot = HealthService.snapshot(services);

    assertEquals(HealthSnapshot.UP, snapshot.status());
    assertEquals("UP", snapshot.database().status());
    assertEquals("SUCCEEDED", snapshot.migrations().status());
    assertEquals("1.0.0-test", snapshot.version());
    assertEquals("test", snapshot.environment());
    assertEquals("disk", snapshot.storage());
    assertEquals("smtp", snapshot.mailProvider());
    assertTrue(snapshot.uptimeSeconds() >= 90);
    assertNull(snapshot.database().detail());
  }

  @Test
  @DisplayName("a sleeping/unreachable database degrades the status but is not an error")
  void degradedWhenDbIsDown() {
    AppServices services = TestServices.with(
        new DbHealth(false, 10_000L, Instant.now(), "SQLException (SQLState 08S01): link failure"),
        new MigrationState(MigrationState.Status.SUCCEEDED, 1, null, Instant.now()));

    HealthSnapshot snapshot = HealthService.snapshot(services);

    assertEquals(HealthSnapshot.DEGRADED, snapshot.status());
    assertEquals("DOWN", snapshot.database().status());
    assertTrue(snapshot.database().detail().contains("link failure"));
  }

  @Test
  @DisplayName("a database that is up while migrations are still running is degraded")
  void degradedWhileMigrationsRun() {
    AppServices services = TestServices.with(
        new DbHealth(true, 2L, Instant.now(), null),
        new MigrationState(MigrationState.Status.RUNNING, 2, "connection refused", null));

    HealthSnapshot snapshot = HealthService.snapshot(services);

    assertEquals(HealthSnapshot.DEGRADED, snapshot.status());
    assertEquals("connection refused", snapshot.migrations().error());
  }
}
