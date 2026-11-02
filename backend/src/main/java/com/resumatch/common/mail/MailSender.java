package com.resumatch.common.mail;

/**
 * Sends transactional email. One interface, two real implementations:
 *
 * <ul>
 *   <li>{@code SmtpMailSender} - Gmail SMTP with an App Password, for local development.</li>
 *   <li>{@code BrevoApiMailSender} - Brevo HTTP API, for production. Render's free plan blocks
 *       outbound SMTP ports 25/465/587, so HTTPS is the only way out. Ships in Phase 2.</li>
 * </ul>
 *
 * <p>Implementations must never throw: the durable outbox in Phase 8 needs a result, not an
 * exception, so it can retry later instead of losing the message.</p>
 */
public interface MailSender {

  MailResult send(MailMessage message);

  /** "smtp", "brevo" or "noop" - shown in /health and in the admin log. */
  String provider();
}
