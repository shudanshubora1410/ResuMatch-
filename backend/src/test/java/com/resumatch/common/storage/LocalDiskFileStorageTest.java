package com.resumatch.common.storage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.resumatch.common.exception.ApiException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalDiskFileStorageTest {

  private static final byte[] PDF_BYTES = "%PDF-1.7 fake content".getBytes(StandardCharsets.UTF_8);

  @Test
  @DisplayName("stores bytes under a random UUID name and reads them back unchanged")
  void storeAndReadRoundTrip(@TempDir Path tempDir) {
    LocalDiskFileStorage storage = new LocalDiskFileStorage(tempDir);

    StoredFile stored = storage.store(PDF_BYTES, "My Resume.pdf", "application/pdf");

    assertTrue(stored.storageKey().matches("[0-9a-f-]{36}\\.pdf"), stored.storageKey());
    assertEquals("My Resume.pdf", stored.originalFileName());
    assertEquals(PDF_BYTES.length, stored.sizeBytes());
    assertArrayEquals(PDF_BYTES, storage.read(stored.storageKey()));
    assertTrue(storage.exists(stored.storageKey()));
  }

  @Test
  @DisplayName("two uploads of the same file get different keys, so nothing is overwritten")
  void keysAreUnique(@TempDir Path tempDir) {
    LocalDiskFileStorage storage = new LocalDiskFileStorage(tempDir);

    StoredFile first = storage.store(PDF_BYTES, "cv.pdf", "application/pdf");
    StoredFile second = storage.store(PDF_BYTES, "cv.pdf", "application/pdf");

    assertFalse(first.storageKey().equals(second.storageKey()));
  }

  @Test
  @DisplayName("an odd extension is dropped instead of being trusted")
  void unsafeExtensionIsDropped(@TempDir Path tempDir) {
    LocalDiskFileStorage storage = new LocalDiskFileStorage(tempDir);

    StoredFile stored = storage.store(PDF_BYTES, "cv.p hp/../x", "text/plain");

    assertEquals(36, stored.storageKey().length(), "key is a bare UUID: " + stored.storageKey());
  }

  @Test
  @DisplayName("path traversal keys are rejected, so the storage folder cannot be escaped")
  void pathTraversalIsBlocked(@TempDir Path tempDir) {
    LocalDiskFileStorage storage = new LocalDiskFileStorage(tempDir);

    ApiException failure = assertThrows(ApiException.class, () -> storage.read("../../etc/passwd"));

    assertEquals(400, failure.getStatus());
  }

  @Test
  @DisplayName("reading an unknown key gives a 404 error, not an IOException")
  void missingFileIsNotFound(@TempDir Path tempDir) {
    LocalDiskFileStorage storage = new LocalDiskFileStorage(tempDir);

    ApiException failure = assertThrows(ApiException.class, () -> storage.read("nope.pdf"));

    assertEquals(404, failure.getStatus());
  }

  @Test
  @DisplayName("delete is idempotent, so a retried request cannot fail")
  void deleteIsIdempotent(@TempDir Path tempDir) {
    LocalDiskFileStorage storage = new LocalDiskFileStorage(tempDir);
    StoredFile stored = storage.store(PDF_BYTES, "cv.pdf", "application/pdf");

    storage.delete(stored.storageKey());
    storage.delete(stored.storageKey());

    assertFalse(storage.exists(stored.storageKey()));
  }

  @Test
  @DisplayName("an empty upload is rejected")
  void emptyContentIsRejected(@TempDir Path tempDir) {
    LocalDiskFileStorage storage = new LocalDiskFileStorage(tempDir);

    ApiException failure = assertThrows(ApiException.class,
        () -> storage.store(new byte[0], "cv.pdf", "application/pdf"));

    assertEquals(400, failure.getStatus());
  }

  @Test
  @DisplayName("the storage reports which implementation is active")
  void reportsItsName(@TempDir Path tempDir) {
    assertEquals("disk", new LocalDiskFileStorage(tempDir).name());
  }
}
