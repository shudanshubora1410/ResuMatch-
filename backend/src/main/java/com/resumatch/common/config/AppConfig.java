package com.resumatch.common.config;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Immutable configuration for the whole application.
 *
 * <p>Values come from two places: an optional git-ignored {@code .env} file (local development)
 * and the real OS environment (production, Eclipse run configuration, CI). The OS environment
 * always wins, because that is how Render / GitHub Actions inject secrets.</p>
 *
 * <p>Nothing in this class ever logs a value, only key names, so passwords cannot leak.</p>
 */
public final class AppConfig {

  // ---- Environment variable names (one place, so typos are compile-time visible) ----
  public static final String DB_URL = "DB_URL";
  public static final String DB_USER = "DB_USER";
  public static final String DB_PASSWORD = "DB_PASSWORD";
  public static final String DB_POOL_MAX = "DB_POOL_MAX";

  public static final String FILE_STORAGE = "FILE_STORAGE";
  public static final String FILE_STORAGE_DIR = "FILE_STORAGE_DIR";

  public static final String MAIL_PROVIDER = "MAIL_PROVIDER";
  public static final String MAIL_FROM = "MAIL_FROM";
  public static final String MAIL_FROM_NAME = "MAIL_FROM_NAME";
  public static final String SMTP_HOST = "SMTP_HOST";
  public static final String SMTP_PORT = "SMTP_PORT";
  public static final String SMTP_USER = "SMTP_USER";
  public static final String SMTP_PASSWORD = "SMTP_PASSWORD";

  public static final String SUPPORT_EMAIL = "SUPPORT_EMAIL";
  public static final String APP_NAME = "APP_NAME";
  public static final String APP_ENV = "APP_ENV";

  /** Default support address used in emails and in the footer; overridable through env. */
  public static final String DEFAULT_SUPPORT_EMAIL = "support.resumatch@gmail.com";

  private final Map<String, String> values;

  AppConfig(Map<String, String> values) {
    // Defensive copy: config must not change after startup.
    this.values = Map.copyOf(new LinkedHashMap<>(values));
  }

  /** Raw lookup; returns null when unset or blank. */
  public String get(String key) {
    String value = values.get(key);
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  public String get(String key, String defaultValue) {
    String value = get(key);
    return value == null ? defaultValue : value;
  }

  /**
   * Reads a value that the application cannot run without.
   *
   * <p>Throws instead of returning null so a missing secret fails loudly at startup rather
   * than producing a confusing NullPointerException on the first request.</p>
   */
  public String require(String key) {
    String value = get(key);
    if (value == null) {
      throw new MissingConfigException(key);
    }
    return value;
  }

  public boolean requireAll(String... keys) {
    boolean allPresent = true;
    for (String key : keys) {
      if (get(key) == null) {
        allPresent = false;
      }
    }
    return allPresent;
  }

  /** Parses an integer, falling back to the default when unset or malformed. */
  public int getInt(String key, int defaultValue) {
    String value = get(key);
    if (value == null) {
      return defaultValue;
    }
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException ex) {
      return defaultValue;
    }
  }

  /** Accepts true/yes/1 and false/no/0, case-insensitive. */
  public boolean getBoolean(String key, boolean defaultValue) {
    String value = get(key);
    if (value == null) {
      return defaultValue;
    }
    return switch (value.toLowerCase()) {
      case "true", "yes", "1" -> true;
      case "false", "no", "0" -> false;
      default -> defaultValue;
    };
  }

  public String supportEmail() {
    return get(SUPPORT_EMAIL, DEFAULT_SUPPORT_EMAIL);
  }

  public String appName() {
    return get(APP_NAME, "ResuMatch");
  }

  /** Names of the keys that are currently set (never the values) - safe for logs. */
  public Set<String> presentKeys() {
    return values.keySet();
  }
}
