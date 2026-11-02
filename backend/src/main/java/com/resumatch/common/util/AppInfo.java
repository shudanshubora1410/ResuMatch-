package com.resumatch.common.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/** Build information read from {@code app.properties} (the version is filled in by Maven). */
public final class AppInfo {

  private static final String UNKNOWN = "unknown";
  private static final String VERSION = loadVersion();

  private AppInfo() {
    // Utility class.
  }

  public static String version() {
    return VERSION;
  }

  private static String loadVersion() {
    try (InputStream in = AppInfo.class.getResourceAsStream("/app.properties")) {
      if (in == null) {
        return UNKNOWN;
      }
      Properties properties = new Properties();
      properties.load(in);
      String version = properties.getProperty("app.version", UNKNOWN);
      return version.isBlank() ? UNKNOWN : version;
    } catch (IOException ex) {
      return UNKNOWN;
    }
  }
}
