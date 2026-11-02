package com.resumatch.common.mail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.resumatch.common.config.AppConfig;
import com.resumatch.common.config.ConfigLoader;
import jakarta.mail.internet.MimeMessage;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MailSenderTest {

  private static AppConfig config(Map<String, String> overrides) {
    Map<String, String> values = new HashMap<>(Map.of(
        "MAIL_FROM", "support.resumatch@gmail.com",
        "MAIL_FROM_NAME", "ResuMatch",
        "SMTP_HOST", "smtp.gmail.com",
        "SMTP_PORT", "587",
        "SMTP_USER", "support.resumatch@gmail.com",
        "SMTP_PASSWORD", "app-password-placeholder"));
    values.putAll(overrides);
    return ConfigLoader.fromSources(values, Map.of());
  }

  @Test
  @DisplayName("a message without a recipient, subject or body is rejected")
  void messageRequiresItsParts() {
    assertThrows(IllegalArgumentException.class,
        () -> new MailMessage("", "Subject", "<p>Body</p>"));
    assertThrows(IllegalArgumentException.class,
        () -> new MailMessage("a@b.com", " ", "<p>Body</p>"));
    assertThrows(IllegalArgumentException.class,
        () -> new MailMessage("a@b.com", "Subject", ""));
  }

  @Test
  @DisplayName("line breaks are rejected, which blocks email header injection")
  void messageRejectsHeaderInjection() {
    assertThrows(IllegalArgumentException.class,
        () -> new MailMessage("a@b.com\r\nBcc: attacker@evil.com", "Subject", "<p>Body</p>"));
    assertThrows(IllegalArgumentException.class,
        () -> new MailMessage("a@b.com", "Subject\nBcc: attacker@evil.com", "<p>Body</p>"));
  }

  @Test
  @DisplayName("the SMTP message carries From, Reply-To (support inbox) and an HTML body")
  void smtpMessageHeadersAreCorrect() throws Exception {
    SmtpMailSender sender = new SmtpMailSender(config(Map.of()));

    MimeMessage message = sender.buildMessage(
        new MailMessage("candidate@example.com", "Application received", "<p>Hello</p>"));
    // Transport.send() calls this before sending, so the test does the same.
    message.saveChanges();

    jakarta.mail.internet.InternetAddress from =
        (jakarta.mail.internet.InternetAddress) message.getFrom()[0];
    assertEquals("support.resumatch@gmail.com", from.getAddress());
    assertEquals("ResuMatch", from.getPersonal());

    jakarta.mail.internet.InternetAddress replyTo =
        (jakarta.mail.internet.InternetAddress) message.getReplyTo()[0];
    assertEquals("support.resumatch@gmail.com", replyTo.getAddress());
    assertEquals("Application received", message.getSubject());
    assertTrue(message.getContentType().startsWith("text/html"), message.getContentType());
    assertEquals("smtp", sender.provider());
  }

  @Test
  @DisplayName("the factory picks SMTP when credentials exist")
  void factoryPicksSmtpWhenConfigured() {
    MailSender sender = MailSenderFactory.create(config(Map.of()));

    assertEquals("smtp", sender.provider());
  }

  @Test
  @DisplayName("the factory falls back to noop with a clear reason when SMTP is not configured")
  void factoryFallsBackToNoop() {
    AppConfig incomplete = ConfigLoader.fromSources(
        Map.of("MAIL_FROM", "support.resumatch@gmail.com", "SMTP_HOST", "smtp.gmail.com"),
        Map.of());

    MailSender sender = MailSenderFactory.create(incomplete);
    MailResult result = sender.send(new MailMessage("a@b.com", "S", "<p>B</p>"));

    assertEquals("noop", sender.provider());
    assertFalse(result.sent());
    assertTrue(result.detail().contains("SMTP_HOST"), result.detail());
  }

  @Test
  @DisplayName("brevo is not wired up until Phase 2, and says so instead of pretending")
  void brevoIsNotImplementedYet() {
    MailSender sender = MailSenderFactory.create(config(Map.of("MAIL_PROVIDER", "brevo")));

    assertEquals("noop", sender.provider());
    MailResult result = sender.send(new MailMessage("a@b.com", "S", "<p>B</p>"));
    assertTrue(result.detail().contains("Phase 2"), result.detail());
  }
}
