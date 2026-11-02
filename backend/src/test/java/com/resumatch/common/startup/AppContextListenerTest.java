package com.resumatch.common.startup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.resumatch.common.config.AppConfig;
import com.resumatch.common.config.ConfigLoader;
import com.resumatch.common.config.MissingConfigException;
import com.resumatch.common.health.HealthService;
import com.resumatch.testsupport.StubServletContext;
import com.resumatch.testsupport.TestDatabase;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AppContextListenerTest {

  @Test
  @DisplayName("a missing mandatory setting aborts startup instead of booting half-configured")
  void missingConfigAbortsStartup() {
    ServletContext context = StubServletContext.create();
    AppContextListener listener =
        new AppContextListener(() -> ConfigLoader.fromSources(Map.of(), Map.of()));

    assertThrows(MissingConfigException.class,
        () -> listener.contextInitialized(new ServletContextEvent(context)));
  }

  @Test
  @DisplayName("startup publishes AppServices, and shutdown removes them")
  void startupPublishesServices(@TempDir java.nio.file.Path tempDir) {
    TestDatabase.assumeAvailable();

    ServletContext context = StubServletContext.create();
    ServletContextEvent event = new ServletContextEvent(context);
    AppContextListener listener = new AppContextListener(
        () -> withStorageDir(TestDatabase.config(), tempDir));

    listener.contextInitialized(event);
    try {
      AppServices services = AppServices.from(context);
      assertNotNull(services, "AppServices must be published in the ServletContext");
      assertEquals(TestDatabase.config().require(AppConfig.DB_URL),
          services.config().require(AppConfig.DB_URL));
      assertEquals("disk", services.fileStorage().name());
      assertNotNull(services.mailSender());
      assertNotNull(services.version());
    } finally {
      listener.contextDestroyed(event);
    }
    assertNull(AppServices.from(context), "shutdown must clean the ServletContext");
  }

  @Test
  @DisplayName("health is UP once the real database and the migrations are ready")
  void healthBecomesUp() throws Exception {
    TestDatabase.assumeAvailable();

    ServletContext context = StubServletContext.create();
    ServletContextEvent event = new ServletContextEvent(context);
    AppContextListener listener = new AppContextListener(() -> {
      AppConfig base = TestDatabase.config();
      return ConfigLoader.fromSources(Map.of(
          AppConfig.DB_URL, base.require(AppConfig.DB_URL),
          AppConfig.DB_USER, base.require(AppConfig.DB_USER),
          AppConfig.DB_PASSWORD, base.get(AppConfig.DB_PASSWORD, ""),
          AppConfig.FILE_STORAGE_DIR, "target/test-uploads"), Map.of());
    });

    listener.contextInitialized(event);
    try {
      AppServices services = AppServices.from(context);
      assertNotNull(services);

      // Flyway and the first health probe both run on background threads.
      long deadline = System.currentTimeMillis() + 30_000;
      while (!services.databaseBootstrap().state().isSucceeded()) {
        assertTrue(System.currentTimeMillis() < deadline, "Flyway did not finish in time");
        Thread.sleep(100);
      }
      while (!services.dbHealthMonitor().latest().up()) {
        assertTrue(System.currentTimeMillis() < deadline, "Health probe did not succeed in time");
        Thread.sleep(100);
      }

      assertEquals("UP", HealthService.snapshot(services).status());
    } finally {
      listener.contextDestroyed(event);
    }
  }

  private static AppConfig withStorageDir(AppConfig base, java.nio.file.Path tempDir) {
    return ConfigLoader.fromSources(Map.of(
        AppConfig.DB_URL, base.require(AppConfig.DB_URL),
        AppConfig.DB_USER, base.require(AppConfig.DB_USER),
        AppConfig.DB_PASSWORD, base.get(AppConfig.DB_PASSWORD, ""),
        AppConfig.FILE_STORAGE_DIR, tempDir.toString()), Map.of());
  }
}
