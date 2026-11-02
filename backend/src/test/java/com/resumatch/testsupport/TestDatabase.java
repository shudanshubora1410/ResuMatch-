package com.resumatch.testsupport;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.resumatch.common.config.AppConfig;
import com.resumatch.common.config.ConfigLoader;
import java.util.HashMap;
import java.util.Map;

/**
 * Access to the real MySQL test database.
 *
 * <p>Configuration is read from system properties first ({@code -Dtest.db.url=...}) and then from
 * the environment ({@code TEST_DB_URL}), because environment variables are awkward to set in some
 * Eclipse run configurations while VM arguments are easy.</p>
 *
 * <p>If no test database is configured, integration tests are skipped instead of failing, so
 * {@code mvn test} still works for someone who only wants the unit tests. CI always provides one.</p>
 */
public final class TestDatabase {

  private TestDatabase() {
    // Utility class.
  }

  private static String setting(String systemProperty, String environmentVariable) {
    String fromProperty = System.getProperty(systemProperty);
    if (fromProperty != null && !fromProperty.isBlank()) {
      return fromProperty.trim();
    }
    String fromEnvironment = System.getenv(environmentVariable);
    if (fromEnvironment != null && !fromEnvironment.isBlank()) {
      return fromEnvironment.trim();
    }
    return null;
  }

  public static boolean isAvailable() {
    return setting("test.db.url", "TEST_DB_URL") != null;
  }

  /** Skips the calling test when no test database is configured. */
  public static void assumeAvailable() {
    assumeTrue(isAvailable(),
        "TEST_DB_URL / -Dtest.db.url is not set, so the MySQL integration test is skipped");
  }

  /** Configuration that points the application at the test schema. */
  public static AppConfig config() {
    Map<String, String> values = new HashMap<>();
    values.put(AppConfig.DB_URL, setting("test.db.url", "TEST_DB_URL"));
    values.put(AppConfig.DB_USER, fallback(setting("test.db.user", "TEST_DB_USER"),
        setting("db.user", "DB_USER")));
    values.put(AppConfig.DB_PASSWORD, fallback(setting("test.db.password", "TEST_DB_PASSWORD"),
        setting("db.password", "DB_PASSWORD")));
    values.put(AppConfig.DB_POOL_MAX, "3");
    values.put(AppConfig.APP_ENV, "test");
    return ConfigLoader.fromSources(values, Map.of());
  }

  private static String fallback(String preferred, String alternative) {
    return preferred != null ? preferred : alternative;
  }
}
