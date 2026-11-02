package com.resumatch.common.mail;

import com.resumatch.common.config.AppConfig;
import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sends mail through an SMTP server (Gmail with an App Password during local development).
 *
 * <p>All timeouts are short on purpose: a stuck SMTP socket must not hold a request or the
 * background mail worker for minutes.</p>
 */
public final class SmtpMailSender implements MailSender {

  private static final Logger log = LoggerFactory.getLogger(SmtpMailSender.class);

  private final Session session;
  private final String fromAddress;
  private final String fromName;
  private final String replyToAddress;

  public SmtpMailSender(AppConfig config) {
    this.fromAddress = config.require(AppConfig.MAIL_FROM);
    this.fromName = config.get(AppConfig.MAIL_FROM_NAME, config.appName());
    // Reply-To and the visible "contact us" address are the same support inbox.
    this.replyToAddress = config.supportEmail();

    Properties properties = new Properties();
    properties.put("mail.smtp.host", config.require(AppConfig.SMTP_HOST));
    properties.put("mail.smtp.port", String.valueOf(config.getInt(AppConfig.SMTP_PORT, 587)));
    properties.put("mail.smtp.auth", "true");
    properties.put("mail.smtp.starttls.enable", "true");
    properties.put("mail.smtp.starttls.required", "true");
    properties.put("mail.smtp.connectiontimeout", "10000");
    properties.put("mail.smtp.timeout", "10000");
    properties.put("mail.smtp.writetimeout", "10000");

    String user = config.require(AppConfig.SMTP_USER);
    String password = config.require(AppConfig.SMTP_PASSWORD);

    this.session = Session.getInstance(properties, new Authenticator() {
      @Override
      protected PasswordAuthentication getPasswordAuthentication() {
        return new PasswordAuthentication(user, password);
      }
    });
    // Never log the credentials: only host/port/from are shown.
    log.info("SMTP mail sender ready ({}:{}, from {})",
        config.get(AppConfig.SMTP_HOST), config.get(AppConfig.SMTP_PORT, "587"), fromAddress);
  }

  @Override
  public MailResult send(MailMessage message) {
    try {
      MimeMessage mimeMessage = buildMessage(message);
      Transport.send(mimeMessage);
      return MailResult.success(provider(), "accepted by SMTP server");
    } catch (MessagingException | UnsupportedEncodingException ex) {
      // Failure is reported, not thrown: the outbox decides whether to retry.
      String reason = ex.getClass().getSimpleName() + ": " + shorten(ex.getMessage());
      log.warn("SMTP send failed for subject '{}': {}", message.subject(), reason);
      return MailResult.failure(provider(), reason);
    }
  }

  /**
   * Builds the MimeMessage. Package-private so a unit test can assert headers, From, Reply-To and
   * the HTML content type without opening a network connection.
   */
  MimeMessage buildMessage(MailMessage message)
      throws MessagingException, UnsupportedEncodingException {
    MimeMessage mimeMessage = new MimeMessage(session);
    mimeMessage.setFrom(new InternetAddress(fromAddress, fromName, "UTF-8"));
    mimeMessage.setReplyTo(new InternetAddress[] {new InternetAddress(replyToAddress)});
    mimeMessage.setRecipient(Message.RecipientType.TO, new InternetAddress(message.to()));
    mimeMessage.setSubject(message.subject(), "UTF-8");
    mimeMessage.setContent(message.htmlBody(), "text/html; charset=UTF-8");
    return mimeMessage;
  }

  @Override
  public String provider() {
    return "smtp";
  }

  private static String shorten(String value) {
    if (value == null) {
      return "";
    }
    return value.length() <= 160 ? value : value.substring(0, 160) + "...";
  }
}
