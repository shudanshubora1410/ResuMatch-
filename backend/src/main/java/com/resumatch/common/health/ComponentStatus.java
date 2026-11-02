package com.resumatch.common.health;

import java.time.Instant;

/**
 * Status of one dependency (currently the database).
 *
 * @param status    UP or DOWN
 * @param latencyMs how long the last probe took
 * @param checkedAt when the last probe ran (null before the first probe)
 * @param detail    short failure reason, null when healthy
 */
public record ComponentStatus(String status, long latencyMs, Instant checkedAt, String detail) {
}
