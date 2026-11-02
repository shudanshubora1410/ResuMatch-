package com.resumatch.common.storage;

import com.resumatch.common.config.AppConfig;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Picks the file storage implementation from {@code FILE_STORAGE=disk|db}. */
public final class FileStorageFactory {

  private static final Logger log = LoggerFactory.getLogger(FileStorageFactory.class);

  private FileStorageFactory() {
    // Utility class.
  }

  public static FileStorage create(AppConfig config) {
    String provider = config.get(AppConfig.FILE_STORAGE, "disk").toLowerCase();

    if ("db".equals(provider)) {
      // DbFileStorage arrives in Phase 3. Until then we fall back to disk and say so loudly,
      // because silently writing to an ephemeral disk in production would lose data.
      log.warn("FILE_STORAGE=db requested but DbFileStorage ships in Phase 3; using disk storage for now.");
      return createDisk(config);
    }
    if (!"disk".equals(provider)) {
      log.warn("Unknown FILE_STORAGE='{}' (expected disk or db); using disk storage.", provider);
    }
    return createDisk(config);
  }

  private static FileStorage createDisk(AppConfig config) {
    String directory = config.get(AppConfig.FILE_STORAGE_DIR, "uploads");
    return new LocalDiskFileStorage(Path.of(directory));
  }
}
