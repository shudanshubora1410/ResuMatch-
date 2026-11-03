package com.resumatch.common.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ConfigLoaderTest {

  @Test
  @DisplayName("parses simple KEY=value lines and ignores comments and blanks")
  void parsesSimpleLines() {
    String content = "# a comment\n"
        + "DB_URL=jdbc:mysql://localhost:3306/resumatch\n"
        + "\n"
        + "export DB_USER=resumatch_app\n"
        + "DB_PASSWORD='secret with spaces'\n"
        + "SUPPORT_EMAIL=\"support.resumatch@gmail.com\"\n";

    Map<String, String> parsed = ConfigLoader.parseDotEnv(content);

    assertEquals(4, parsed.size());
    assertEquals("jdbc:mysql://localhost:3306/resumatch", parsed.get("DB_URL"));
    assertEquals("resumatch_app", parsed.get("DB_USER"));
    assertEquals("secret with spaces", parsed.get("DB_PASSWORD"));
    assertEquals("support.resumatch@gmail.com", parsed.get("SUPPORT_EMAIL"));
  }

  @Test
  @DisplayName("keeps '=' characters that belong to the value (JDBC URLs contain them)")
  void keepsEqualsInsideValues() {
    Map<String, String> parsed = ConfigLoader.parseDotEnv(
        "DB_URL=jdbc:mysql://host:3306/db?sslMode=REQUIRED&useSSL=true");

    assertEquals("jdbc:mysql://host:3306/db?sslMode=REQUIRED&useSSL=true", parsed.get("DB_URL"));
  }

  @Test
  @DisplayName("the process environment overrides the .env file")
  void environmentOverridesFile() {
    AppConfig config = ConfigLoader.fromSources(
        Map.of("DB_USER", "from-file", "FILE_ONLY", "file-value"),
        Map.of("DB_USER", "from-env"));

    assertEquals("from-env", config.get("DB_USER"));
    assertEquals("file-value", config.get("FILE_ONLY"));
  }

  @Test
  @DisplayName("require() throws with the variable name and never with its value")
  void requireFailsLoudly() {
    AppConfig config = ConfigLoader.fromSources(Map.of(), Map.of());

    MissingConfigException failure =
        assertThrows(MissingConfigException.class, () -> config.require("DB_URL"));

    assertEquals("DB_URL", failure.getKey());
    assertTrue(failure.getMessage().contains("DB_URL"));
  }

  @Test
  @DisplayName("blank values are treated as missing so a half-filled .env cannot pass")
  void blankValuesAreMissing() {
    AppConfig config = ConfigLoader.fromSources(Map.of("DB_PASSWORD", "   "), Map.of());

    assertNull(config.get("DB_PASSWORD"));
  }

  @Test
  @DisplayName("typed getters fall back safely instead of throwing")
  void typedGettersFallBack() {
    AppConfig config = ConfigLoader.fromSources(
        Map.of("DB_POOL_MAX", "not-a-number", "APP_ENV", "production"), Map.of());

    assertEquals(5, config.getInt("DB_POOL_MAX", 5));
    assertEquals(7, config.getInt("MISSING", 7));
    assertTrue(config.getBoolean("MISSING", true));
    assertEquals("production", config.get("APP_ENV"));
  }

  @Test
  @DisplayName("booleans accept the usual spellings")
  void booleansAcceptCommonSpellings() {
    AppConfig config = ConfigLoader.fromSources(
        Map.of("A", "true", "B", "YES", "C", "0", "D", "nonsense"), Map.of());

    assertTrue(config.getBoolean("A", false));
    assertTrue(config.getBoolean("B", false));
    assertFalse(config.getBoolean("C", true));
    assertTrue(config.getBoolean("D", true), "unknown text keeps the default");
  }

  @Test
  @DisplayName("support email defaults to the project address")
  void supportEmailHasADefault() {
    AppConfig config = ConfigLoader.fromSources(Map.of(), Map.of());

    assertEquals(AppConfig.DEFAULT_SUPPORT_EMAIL, config.supportEmail());
  }

  @Test
  @DisplayName("requireAll reports whether every named variable is set")
  void requireAllChecksEveryKey() {
    AppConfig config = ConfigLoader.fromSources(Map.of("SMTP_HOST", "smtp.gmail.com"), Map.of());

    assertFalse(config.requireAll("SMTP_HOST", "SMTP_USER"));
    assertTrue(config.requireAll("SMTP_HOST"));
  }

  @Test
  void envVariableWinsOverSystemProperty() {
    assertEquals("/from/env/.env",
        ConfigLoader.resolveEnvFilePath("/from/env/.env", "/from/property/.env", null));
  }

  @Test
  void upperCaseSystemPropertyIsUsedWhenEnvIsMissing() {
    assertEquals("/from/property/.env",
        ConfigLoader.resolveEnvFilePath(null, "/from/property/.env", null));
  }

  @Test
  void namedSystemPropertyIsUsedWhenOthersAreMissing() {
    assertEquals("/from/named/.env",
        ConfigLoader.resolveEnvFilePath(null, "  ", "/from/named/.env"));
  }

  @Test
  void fallsBackToDotEnvInWorkingDirectory() {
    assertEquals(".env", ConfigLoader.resolveEnvFilePath(null, null, null));
    assertEquals(".env", ConfigLoader.resolveEnvFilePath("", " ", null));
  }

}
