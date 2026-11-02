package com.resumatch.common.startup;

import com.resumatch.common.config.AppConfig;
import com.resumatch.common.config.ConfigLoader;
import com.resumatch.common.db.Database;
import com.resumatch.common.db.DatabaseBootstrap;
import com.resumatch.common.db.DbHealthMonitor;
import com.resumatch.common.mail.MailSender;
import com.resumatch.common.mail.MailSenderFactory;
import com.resumatch.common.storage.FileStorage;
import com.resumatch.common.storage.FileStorageFactory;
import com.resumatch.common.util.AppInfo;
import com.resumatch.common.util.JdbcUrls;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Enumeration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Application entry point (declared in {@code web.xml}).
 *
 * <p>Startup order, and why:</p>
 * <ol>
 *   <li>Load configuration - fail fast if a mandatory secret is missing.</li>
 *   <li>Create the connection pool - does not require a live database.</li>
 *   <li>Start the background Flyway run - retries with backoff if the DB is asleep.</li>
 *   <li>Start the health monitor - gives {@code /health} a cached, non-blocking DB status.</li>
 *   <li>Create file storage and the mail sender from env vars.</li>
 * </ol>
 */
public final class AppContextListener implements ServletContextListener {

  private static final Logger log = LoggerFactory.getLogger(AppContextListener.class);

  private final Supplier<AppConfig> configSource;
  private ExecutorService startupExecutor;

  public AppContextListener() {
    this(ConfigLoader::load);
  }

  /** Injection point for tests: they supply a fixed configuration instead of env variables. */
  AppContextListener(Supplier<AppConfig> configSource) {
    this.configSource = configSource;
  }

  @Override
  public void contextInitialized(ServletContextEvent event) {
    ServletContext context = event.getServletContext();
    try {
      AppConfig config = configSource.get();
      String environment = config.get(AppConfig.APP_ENV, "local");

      log.info("Starting {} {} ({})", config.appName(), AppInfo.version(), environment);

      // A missing DB_URL / DB_USER stops the deployment on purpose: better a loud failure now
      // than confusing 500s on every request later.
      Database database = Database.create(config);
      FileStorage fileStorage = FileStorageFactory.create(config);
      MailSender mailSender = MailSenderFactory.create(config);

      DbHealthMonitor healthMonitor = new DbHealthMonitor(database);
      healthMonitor.start();

      DatabaseBootstrap bootstrap = new DatabaseBootstrap(database);
      startupExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "flyway-bootstrap");
        thread.setDaemon(true);
        return thread;
      });
      startupExecutor.submit(bootstrap);

      AppServices services = new AppServices(config, database, fileStorage, mailSender, bootstrap,
          healthMonitor, AppInfo.version(), environment, Instant.now());
      context.setAttribute(AppServices.ATTRIBUTE, services);

      log.info("Startup complete: db={}, storage={}, mail={}, support={}",
          JdbcUrls.maskForLog(config.get(AppConfig.DB_URL)),
          fileStorage.name(), mailSender.provider(), config.supportEmail());
    } catch (RuntimeException ex) {
      // Configuration errors must stop the app; the message names the variable, never its value.
      log.error("Application startup failed: {}", ex.getMessage(), ex);
      throw ex;
    }
  }

  @Override
  public void contextDestroyed(ServletContextEvent event) {
    log.info("Shutting down");
    AppServices services = AppServices.from(event.getServletContext());
    if (services != null) {
      services.dbHealthMonitor().stop();
    }
    if (startupExecutor != null) {
      startupExecutor.shutdownNow();
    }
    if (services != null) {
      services.database().close();
    }
    releaseDatabaseResources();
    event.getServletContext().removeAttribute(AppServices.ATTRIBUTE);
  }

  /**
   * Releases the JDBC driver and MySQL's abandoned-connection thread.
   *
   * <p>Tomcat keeps a reference to both through the web-app class loader, which produces the
   * "failed to unregister it ... will create a memory leak" warnings on every redeploy. Cleaning
   * up explicitly keeps hot redeploys leak-free.</p>
   */
  private static void releaseDatabaseResources() {
    try {
      com.mysql.cj.jdbc.AbandonedConnectionCleanupThread.checkedShutdown();
    } catch (RuntimeException ex) {
      log.debug("MySQL cleanup thread shutdown skipped: {}", ex.getMessage());
    }

    Enumeration<Driver> drivers = DriverManager.getDrivers();
    while (drivers.hasMoreElements()) {
      Driver driver = drivers.nextElement();
      // Only touch drivers loaded by this web application, never the container's own.
      if (driver.getClass().getClassLoader() == AppContextListener.class.getClassLoader()) {
        try {
          DriverManager.deregisterDriver(driver);
        } catch (SQLException ex) {
          log.debug("Could not deregister JDBC driver: {}", ex.getMessage());
        }
      }
    }
  }
}
