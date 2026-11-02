package com.resumatch.common.config;

/** Thrown at startup when a mandatory configuration value is missing. */
public class MissingConfigException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final String key;

  public MissingConfigException(String key) {
    super("Required configuration value is missing: " + key
        + ". Set it as an environment variable (or in your local .env file). See .env.example.");
    this.key = key;
  }

  public String getKey() {
    return key;
  }
}
