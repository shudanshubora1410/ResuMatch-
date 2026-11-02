package com.resumatch.common.health;

/**
 * Flyway progress as shown by the health endpoint.
 *
 * @param status PENDING, RUNNING, SUCCEEDED or FAILED
 * @param error  short error line when the last attempt failed, otherwise null
 */
public record MigrationStatusView(String status, String error) {
}
