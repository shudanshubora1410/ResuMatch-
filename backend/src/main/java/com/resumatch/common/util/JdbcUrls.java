package com.resumatch.common.util;

import java.util.regex.Pattern;

/** Helpers for printing JDBC URLs safely: host and database stay, credentials never appear. */
public final class JdbcUrls {

  private static final Pattern PASSWORD_PARAM = Pattern.compile("(?i)(password=)[^&;]*");
  private static final Pattern USER_PARAM = Pattern.compile("(?i)(user=)[^&;]*");

  private JdbcUrls() {
    // Utility class.
  }

  /**
   * Masks credentials inside a JDBC URL for logging.
   *
   * <p>Exported mainly for tests, but also used by the startup log so we can confirm which
   * database we connected to without leaking the password into Render's log viewer.</p>
   */
  public static String maskForLog(String jdbcUrl) {
    if (jdbcUrl == null || jdbcUrl.isBlank()) {
      return "(not set)";
    }
    String masked = PASSWORD_PARAM.matcher(jdbcUrl).replaceAll("$1***");
    masked = USER_PARAM.matcher(masked).replaceAll("$1***");
    return masked;
  }
}
