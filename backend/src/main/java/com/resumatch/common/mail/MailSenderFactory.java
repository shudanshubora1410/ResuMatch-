package com.resumatch.common.mail;

import com.resumatch.common.config.AppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Picks the mail implementation from {@code MAIL_PROVIDER=smtp|brevo}. */
public final class MailSenderFactory {

  private static final Logger log = LoggerFactory.getLogger(MailSenderFactory.class);

  private MailSenderFactory() {
    // Utility class.
  }

  public static MailSender create(AppConfig config) {
    String provider = config.get(AppConfig.MAIL_PROVIDER, "smtp").toLowerCase();

    if ("brevo".equals(provider)) {
      // BrevoApiMailSender arrives in Phase 2, together with the production email setup.
      return new NoopMailSender("MAIL_PROVIDER=brevo is not implemented until Phase 2");
    }
    if (!"smtp".equals(provider)) {
      log.warn("Unknown MAIL_PROVIDER='{}' (expected smtp or brevo); email disabled.", provider);
      return new NoopMailSender("Unknown MAIL_PROVIDER value");
    }

    boolean smtpConfigured = config.requireAll(
        AppConfig.SMTP_HOST, AppConfig.SMTP_USER, AppConfig.SMTP_PASSWORD, AppConfig.MAIL_FROM);
    if (!smtpConfigured) {
      return new NoopMailSender(
          "SMTP_HOST / SMTP_USER / SMTP_PASSWORD / MAIL_FROM are not all set");
    }
    return new SmtpMailSender(config);
  }
}
