package com.resumatch.common.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JdbcUrlsTest {

  @Test
  @DisplayName("credentials in the JDBC URL never reach the logs")
  void masksCredentials() {
    String masked = JdbcUrls.maskForLog(
        "jdbc:mysql://db.example.com:12345/defaultdb?user=avnadmin&password=SuperSecret1&sslMode=REQUIRED");

    assertFalse(masked.contains("SuperSecret1"), masked);
    assertFalse(masked.contains("avnadmin"), masked);
    assertTrue(masked.contains("db.example.com:12345/defaultdb"), masked);
    // Non-secret parameters stay visible, which helps diagnose SSL problems.
    assertTrue(masked.contains("sslMode=REQUIRED"), masked);
  }

  @Test
  @DisplayName("a URL without credentials is unchanged")
  void leavesPlainUrlAlone() {
    assertEquals("jdbc:mysql://localhost:3306/resumatch",
        JdbcUrls.maskForLog("jdbc:mysql://localhost:3306/resumatch"));
  }

  @Test
  @DisplayName("a missing URL is reported, never printed as null")
  void handlesMissingUrl() {
    assertEquals("(not set)", JdbcUrls.maskForLog(null));
    assertEquals("(not set)", JdbcUrls.maskForLog("  "));
  }

  @Test
  @DisplayName("app.properties is filtered at build time, so the version is real")
  void versionIsFilteredIn() {
    assertTrue(AppInfo.version().matches("1\\.0\\.0.*"), AppInfo.version());
  }
}
