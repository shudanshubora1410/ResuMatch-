package com.resumatch.common.storage;

import java.time.Instant;

/**
 * Reference to a stored file. This is what gets saved in the database, never the bytes themselves
 * (except for {@code DbFileStorage}, where the bytes live in a dedicated blob table).
 *
 * @param storageKey       opaque key; the filesystem name on disk, or the blob row key in the DB
 * @param originalFileName original upload name, sanitised for display
 * @param contentType      MIME type detected from the file's magic bytes
 * @param sizeBytes        size of the stored content
 * @param storedAt         when it was written
 */
public record StoredFile(String storageKey, String originalFileName, String contentType,
                         long sizeBytes, Instant storedAt) {
}
