package com.resumatch.common.mail;

/**
 * Outcome of one send attempt.
 *
 * @param sent     true when the provider accepted the message
 * @param provider which implementation handled it
 * @param detail   provider message id on success, or a short reason on failure (no secrets)
 */
public record MailResult(boolean sent, String provider, String detail) {

  public static MailResult success(String provider, String detail) {
    return new MailResult(true, provider, detail);
  }

  public static MailResult failure(String provider, String detail) {
    return new MailResult(false, provider, detail);
  }
}
