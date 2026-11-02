package com.resumatch.common.startup;

import com.resumatch.common.config.AppConfig;
import com.resumatch.common.db.Database;
import com.resumatch.common.db.DatabaseBootstrap;
import com.resumatch.common.db.DbHealthMonitor;
import com.resumatch.common.mail.MailSender;
import com.resumatch.common.storage.FileStorage;
import jakarta.servlet.ServletContext;
import java.time.Instant;

/**
 * Everything the application needs at runtime, created once at startup and stored in the
 * {@link ServletContext}.
 *
 * <p>This is a deliberate "poor man's dependency injection container": explicit, readable and
 * exactly one instance per web application, with no framework magic to explain in a viva.</p>
 */
public record AppServices(
    AppConfig config,
    Database database,
    FileStorage fileStorage,
    MailSender mailSender,
    DatabaseBootstrap databaseBootstrap,
    DbHealthMonitor dbHealthMonitor,
    String version,
    String environment,
    Instant startedAt) {

  public static final String ATTRIBUTE = "com.resumatch.AppServices";

  /** Returns the services, or null when startup did not get far enough. */
  public static AppServices from(ServletContext context) {
    Object value = context.getAttribute(ATTRIBUTE);
    return value instanceof AppServices services ? services : null;
  }
}
