package com.resumatch.common.storage;

import com.resumatch.common.exception.ApiException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stores files on the local filesystem, outside the web root.
 *
 * <p>The filename is a random UUID, so a guessable URL cannot be used to fetch somebody else's
 * resume even if the folder were exposed by mistake. Files are only ever served through an
 * authenticated servlet.</p>
 */
public final class LocalDiskFileStorage implements FileStorage {

  private static final Logger log = LoggerFactory.getLogger(LocalDiskFileStorage.class);

  private final Path root;

  public LocalDiskFileStorage(Path root) {
    this.root = root.toAbsolutePath().normalize();
    try {
      Files.createDirectories(this.root);
    } catch (IOException ex) {
      throw new UncheckedIOException("Cannot create file storage directory: " + this.root, ex);
    }
    log.info("Local disk file storage ready at {}", this.root);
  }

  @Override
  public StoredFile store(byte[] content, String originalFileName, String contentType) {
    if (content == null || content.length == 0) {
      throw ApiException.badRequest("File is empty.");
    }
    String storageKey = UUID.randomUUID() + extensionOf(originalFileName);
    Path target = resolve(storageKey);
    try {
      // Write to a temp file first, then move: a crash cannot leave half a resume behind.
      Path temp = Files.createTempFile(root, "upload-", ".tmp");
      Files.write(temp, content);
      Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException ex) {
      throw new UncheckedIOException("Could not store uploaded file", ex);
    }
    return new StoredFile(storageKey, originalFileName, contentType, content.length, Instant.now());
  }

  @Override
  public byte[] read(String storageKey) {
    Path file = resolve(storageKey);
    if (!Files.isReadable(file)) {
      throw ApiException.notFound("File not found.");
    }
    try {
      return Files.readAllBytes(file);
    } catch (IOException ex) {
      throw new UncheckedIOException("Could not read stored file", ex);
    }
  }

  @Override
  public boolean exists(String storageKey) {
    return storageKey != null && Files.isReadable(resolve(storageKey));
  }

  @Override
  public void delete(String storageKey) {
    if (storageKey == null) {
      return;
    }
    try {
      Files.deleteIfExists(resolve(storageKey));
    } catch (IOException ex) {
      log.warn("Could not delete stored file {}: {}", storageKey, ex.getMessage());
    }
  }

  @Override
  public String name() {
    return "disk";
  }

  /**
   * Blocks path traversal: the resolved path must stay inside the storage root.
   * A key like {@code ../../etc/passwd} therefore throws instead of escaping the folder.
   */
  private Path resolve(String storageKey) {
    if (storageKey == null || storageKey.isBlank()) {
      throw ApiException.badRequest("Invalid file key.");
    }
    Path candidate = root.resolve(storageKey).normalize();
    if (!candidate.startsWith(root)) {
      throw ApiException.badRequest("Invalid file key.");
    }
    return candidate;
  }

  /** Keeps only a short, alphanumeric extension so the key stays safe and readable. */
  private static String extensionOf(String originalFileName) {
    if (originalFileName == null) {
      return "";
    }
    int dot = originalFileName.lastIndexOf('.');
    if (dot < 0 || dot == originalFileName.length() - 1) {
      return "";
    }
    String extension = originalFileName.substring(dot).toLowerCase();
    return extension.matches("\\.[a-z0-9]{1,8}") ? extension : "";
  }
}
