package com.resumatch.common.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A mail sender that sends nothing.
 *
 * <p>Used only when email is not configured yet (for example a fresh clone with no SMTP password,
 * or {@code MAIL_PROVIDER=brevo} before Phase 2 ships it). It keeps the app bootable in every
 * environment while still reporting a clear failure, so nothing silently pretends to have sent.</p>
 */
public final class NoopMailSender implements MailSender {

  private static final Logger log = LoggerFactory.getLogger(NoopMailSender.class);

  private final String reason;

  public NoopMailSender(String reason) {
    this.reason = reason;
    log.warn("Outgoing email is disabled: {}", reason);
  }

  @Override
  public MailResult send(MailMessage message) {
    log.debug("Email not sent ({}): subject '{}'", reason, message.subject());
    return MailResult.failure(provider(), reason);
  }

  @Override
  public String provider() {
    return "noop";
  }
}
