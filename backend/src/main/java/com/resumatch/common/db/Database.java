package com.resumatch.common.db;

import com.resumatch.common.config.AppConfig;
import com.resumatch.common.util.JdbcUrls;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The application's single MySQL connection pool (HikariCP) plus a cheap health probe.
 *
 * <p>Plain JDBC only - no ORM. Every DAO asks this class for a connection.</p>
 */
public final class Database implements AutoCloseable {

  private static final Logger log = LoggerFactory.getLogger(Database.class);

  /** Small pool on purpose: Aiven's free MySQL plan allows roughly 76 connections in total. */
  private static final int DEFAULT_POOL_MAX = 5;

  private final HikariDataSource dataSource;
  private final String maskedJdbcUrl;

  private Database(HikariDataSource dataSource, String maskedJdbcUrl) {
    this.dataSource = dataSource;
    this.maskedJdbcUrl = maskedJdbcUrl;
  }

  /**
   * Creates the pool. Fails if the two mandatory settings ({@code DB_URL}, {@code DB_USER}) are
   * missing, but <b>not</b> if the database is currently unreachable: Aiven free instances power
   * off when idle, and we want the app (and {@code /health}) to come up and wait for the DB.
   */
  public static Database create(AppConfig config) {
    String jdbcUrl = config.require(AppConfig.DB_URL);
    String user = config.require(AppConfig.DB_USER);
    String password = config.get(AppConfig.DB_PASSWORD, "");

    HikariConfig hikari = new HikariConfig();
    hikari.setPoolName("resumatch-pool");
    hikari.setJdbcUrl(jdbcUrl);
    hikari.setUsername(user);
    hikari.setPassword(password);
    hikari.setDriverClassName("com.mysql.cj.jdbc.Driver");
    hikari.setMaximumPoolSize(config.getInt(AppConfig.DB_POOL_MAX, DEFAULT_POOL_MAX));
    hikari.setMinimumIdle(1);
    hikari.setConnectionTimeout(10_000);
    hikari.setValidationTimeout(3_000);
    hikari.setIdleTimeout(300_000);
    // Shorter than Aiven's idle disconnect so we rotate connections before the server drops them.
    hikari.setMaxLifetime(1_500_000);
    hikari.setKeepaliveTime(120_000);
    hikari.setAutoCommit(true);
    // -1 = do not throw at construction time when the DB is asleep; connect lazily instead.
    hikari.setInitializationFailTimeout(-1);

    String masked = JdbcUrls.maskForLog(jdbcUrl);
    log.info("Creating connection pool (max {} connections) for {}", hikari.getMaximumPoolSize(), masked);
    return new Database(new HikariDataSource(hikari), masked);
  }

  public Connection getConnection() throws SQLException {
    return dataSource.getConnection();
  }

  public DataSource getDataSource() {
    return dataSource;
  }

  public String getMaskedJdbcUrl() {
    return maskedJdbcUrl;
  }

  /**
   * Runs {@code SELECT 1} with a short query timeout.
   *
   * <p>Callers must not use this on a request thread: acquiring a connection can block for up to
   * {@code connectionTimeout}. {@link DbHealthMonitor} calls it on a background thread and caches
   * the result.</p>
   */
  public DbHealth ping() {
    Instant startedAt = Instant.now();
    try (Connection connection = dataSource.getConnection();
         Statement statement = connection.createStatement()) {
      statement.setQueryTimeout(3);
      try (ResultSet rs = statement.executeQuery("SELECT 1")) {
        rs.next();
      }
      return new DbHealth(true, java.time.Duration.between(startedAt, Instant.now()).toMillis(),
          Instant.now(), null);
    } catch (SQLException ex) {
      // Only the SQLState/short message: never the JDBC URL, which can contain credentials.
      String detail = ex.getClass().getSimpleName() + " (SQLState " + ex.getSQLState() + "): "
          + truncate(ex.getMessage(), 160);
      log.warn("Database health probe failed: {}", detail);
      return new DbHealth(false, java.time.Duration.between(startedAt, Instant.now()).toMillis(),
          Instant.now(), detail);
    }
  }

  private static String truncate(String value, int max) {
    if (value == null) {
      return "";
    }
    return value.length() <= max ? value : value.substring(0, max) + "...";
  }

  @Override
  public void close() {
    if (!dataSource.isClosed()) {
      log.info("Closing connection pool");
      dataSource.close();
    }
  }
}
