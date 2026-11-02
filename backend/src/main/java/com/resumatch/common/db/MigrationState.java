package com.resumatch.common.db;

import java.time.Instant;

/**
 * Progress of the Flyway migration attempt that runs in the background at startup.
 *
 * @param status    current state
 * @param attempts  how many attempts have been made so far
 * @param lastError short error description of the last failure, or null
 * @param finishedAt when the migration succeeded (null while still not finished)
 */
public record MigrationState(MigrationState.Status status, int attempts, String lastError,
                             Instant finishedAt) {

  public enum Status {
    /** Waiting to start. */
    PENDING,
    /** Currently running migrations. */
    RUNNING,
    /** All migrations applied; the schema is current. */
    SUCCEEDED,
    /** Gave up after the maximum number of attempts. */
    FAILED
  }

  public static MigrationState pending() {
    return new MigrationState(Status.PENDING, 0, null, null);
  }

  public boolean isSucceeded() {
    return status == Status.SUCCEEDED;
  }
}
