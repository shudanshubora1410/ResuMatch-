package com.resumatch.common.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Builds {@link AppConfig} from an optional {@code .env} file plus the OS environment.
 *
 * <p>Why the {@code RESUMATCH_ENV_FILE} override: Tomcat's working directory is
 * {@code $CATALINA_HOME/bin}, so Eclipse/Tomcat will not find a {@code .env} that sits next to
 * the pom. The override lets you point at the file explicitly instead of guessing.</p>
 */
public final class ConfigLoader {

  /** Optional path to a .env file, e.g. C:\projects\resumatch\.env */
  public static final String ENV_FILE_OVERRIDE = "RESUMATCH_ENV_FILE";

  /** Same override as a -D system property, for Eclipse run configurations and container flags. */
  public static final String ENV_FILE_PROPERTY = "resumatch.env.file";

  private static final String DEFAULT_ENV_FILE = ".env";

  private ConfigLoader() {
    // Utility class.
  }

  /**
   * Loads configuration for the running application.
   *
   * <p>Order (later wins): .env file -> OS environment.</p>
   */
  public static AppConfig load() {
    Map<String, String> fileValues = new HashMap<>();

    Path envFile = Path.of(resolveEnvFilePath(System.getenv(ENV_FILE_OVERRIDE),
        System.getProperty(ENV_FILE_OVERRIDE), System.getProperty(ENV_FILE_PROPERTY)));

    if (Files.isReadable(envFile)) {
      try {
        fileValues = parseDotEnv(Files.readString(envFile, StandardCharsets.UTF_8));
      } catch (IOException ex) {
        // A broken .env must not take the app down: real env vars may already be enough.
        fileValues = new HashMap<>();
      }
    }

    return fromSources(fileValues, System.getenv());
  }

  /**
   * Picks the .env path from the three places people actually configure it, in priority order:
   * the OS environment variable, then the -D flags. Falls back to {@code .env} in the working
   * directory, which is what a plain "java -jar" or a CI checkout gets.
   *
   * <p>Why the -D support: Eclipse and Tomcat users set VM arguments, not environment variables,
   * and silently ignoring that argument leaves the app failing with "missing DB_URL".</p>
   */
  public static String resolveEnvFilePath(String fromEnv, String fromProperty, String fromNamedProperty) {
    for (String candidate : new String[] {fromEnv, fromProperty, fromNamedProperty}) {
      if (candidate != null && !candidate.isBlank()) {
        return candidate.trim();
      }
    }
    return DEFAULT_ENV_FILE;
  }

  /** Merge helper, separate from I/O so it is trivial to unit test. */
  public static AppConfig fromSources(Map<String, String> fileValues, Map<String, String> environment) {
    Map<String, String> merged = new HashMap<>();
    if (fileValues != null) {
      merged.putAll(fileValues);
    }
    if (environment != null) {
      // Environment variables override the file, which is how CI and Render inject secrets.
      merged.putAll(environment);
    }
    return new AppConfig(merged);
  }

  /**
   * Parses simple {@code KEY=value} lines.
   *
   * <p>Supported: blank lines, {@code #} comments, optional {@code export} prefix, and values
   * wrapped in single or double quotes. Deliberately not a full dotenv implementation - we only
   * need the obvious cases, and simple code is easier to defend in a viva.</p>
   */
  public static Map<String, String> parseDotEnv(String content) {
    Map<String, String> result = new HashMap<>();
    if (content == null) {
      return result;
    }
    for (String rawLine : content.split("\\R")) {
      String line = rawLine.trim();
      if (line.isEmpty() || line.startsWith("#")) {
        continue;
      }
      if (line.startsWith("export ")) {
        line = line.substring("export ".length()).trim();
      }
      int separator = line.indexOf('=');
      if (separator <= 0) {
        continue; // Not a KEY=value line; ignore instead of failing.
      }
      String key = line.substring(0, separator).trim();
      String value = line.substring(separator + 1).trim();
      value = stripQuotes(value);
      if (!key.isEmpty()) {
        result.put(key, value);
      }
    }
    return result;
  }

  private static String stripQuotes(String value) {
    if (value.length() >= 2) {
      char first = value.charAt(0);
      char last = value.charAt(value.length() - 1);
      boolean singleQuoted = first == '\'' && last == '\'';
      boolean doubleQuoted = first == '"' && last == '"';
      if (singleQuoted || doubleQuoted) {
        return value.substring(1, value.length() - 1);
      }
    }
    return value;
  }
}
