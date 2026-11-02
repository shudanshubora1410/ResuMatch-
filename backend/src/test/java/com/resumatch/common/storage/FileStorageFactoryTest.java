package com.resumatch.common.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.resumatch.common.config.AppConfig;
import com.resumatch.common.config.ConfigLoader;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileStorageFactoryTest {

  @Test
  @DisplayName("disk is the default storage")
  void diskIsTheDefault(@TempDir Path tempDir) {
    AppConfig config = ConfigLoader.fromSources(
        Map.of(AppConfig.FILE_STORAGE_DIR, tempDir.toString()), Map.of());

    assertEquals("disk", FileStorageFactory.create(config).name());
  }

  @Test
  @DisplayName("FILE_STORAGE=db falls back to disk until Phase 3 ships DbFileStorage")
  void dbFallsBackForNow(@TempDir Path tempDir) {
    AppConfig config = ConfigLoader.fromSources(
        Map.of(AppConfig.FILE_STORAGE, "db", AppConfig.FILE_STORAGE_DIR, tempDir.toString()),
        Map.of());

    assertEquals("disk", FileStorageFactory.create(config).name());
  }

  @Test
  @DisplayName("an unknown value never crashes startup")
  void unknownValueFallsBack(@TempDir Path tempDir) {
    AppConfig config = ConfigLoader.fromSources(
        Map.of(AppConfig.FILE_STORAGE, "s3", AppConfig.FILE_STORAGE_DIR, tempDir.toString()),
        Map.of());

    assertEquals("disk", FileStorageFactory.create(config).name());
  }
}
