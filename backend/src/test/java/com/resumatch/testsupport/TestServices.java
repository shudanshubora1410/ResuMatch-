package com.resumatch.testsupport;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.resumatch.common.config.AppConfig;
import com.resumatch.common.config.ConfigLoader;
import com.resumatch.common.db.Database;
import com.resumatch.common.db.DatabaseBootstrap;
import com.resumatch.common.db.DbHealth;
import com.resumatch.common.db.DbHealthMonitor;
import com.resumatch.common.db.MigrationState;
import com.resumatch.common.mail.MailSender;
import com.resumatch.common.startup.AppServices;
import com.resumatch.common.storage.FileStorage;
import java.time.Instant;
import java.util.Map;

/** Builds an {@link AppServices} instance whose dependencies are mocks. */
public final class TestServices {

  private TestServices() {
    // Utility class.
  }

  public static AppServices with(DbHealth dbHealth, MigrationState migrationState) {
    FileStorage fileStorage = mock(FileStorage.class);
    when(fileStorage.name()).thenReturn("disk");

    MailSender mailSender = mock(MailSender.class);
    when(mailSender.provider()).thenReturn("smtp");

    DatabaseBootstrap bootstrap = mock(DatabaseBootstrap.class);
    when(bootstrap.state()).thenReturn(migrationState);

    DbHealthMonitor healthMonitor = mock(DbHealthMonitor.class);
    when(healthMonitor.latest()).thenReturn(dbHealth);

    AppConfig config = ConfigLoader.fromSources(Map.of(), Map.of());
    return new AppServices(config, mock(Database.class), fileStorage, mailSender, bootstrap,
        healthMonitor, "1.0.0-test", "test", Instant.now().minusSeconds(90));
  }

  public static AppServices healthy() {
    return with(new DbHealth(true, 3L, Instant.now(), null),
        new MigrationState(MigrationState.Status.SUCCEEDED, 1, null, Instant.now()));
  }
}
