package com.resumatch.common.storage;

/**
 * Stores binary files (resumes, later generated PDFs) outside the web root.
 *
 * <p>Two implementations exist because hosting differs:</p>
 * <ul>
 *   <li>{@code LocalDiskFileStorage} - local development on Windows/Tomcat.</li>
 *   <li>{@code DbFileStorage} - production. Render's free disk is wiped on every deploy, so the
 *       bytes live in MySQL instead. Ships in Phase 3.</li>
 * </ul>
 */
public interface FileStorage {

  /**
   * Persists the bytes and returns the metadata the database row should keep.
   *
   * @param content          raw file bytes (already validated by the caller)
   * @param originalFileName the name the user uploaded, kept only for display
   * @param contentType      detected MIME type
   */
  StoredFile store(byte[] content, String originalFileName, String contentType);

  /** Reads a file back; throws {@code ApiException} 404 when it does not exist. */
  byte[] read(String storageKey);

  boolean exists(String storageKey);

  /** Deletes silently when already gone, so retries are safe. */
  void delete(String storageKey);

  /** "disk" or "db" - shown in /health so we can see which storage is live. */
  String name();
}
