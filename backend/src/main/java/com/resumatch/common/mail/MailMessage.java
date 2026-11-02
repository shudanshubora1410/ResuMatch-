package com.resumatch.common.mail;

/**
 * One outgoing email.
 *
 * <p>The HTML body is built by the caller from a template. Templates must escape every dynamic
 * value; that rule is enforced in Phase 8 when the shared templates are introduced.</p>
 *
 * @param to      recipient address
 * @param subject plain-text subject line
 * @param htmlBody HTML body, already rendered with escaped values
 */
public record MailMessage(String to, String subject, String htmlBody) {

  public MailMessage {
    if (to == null || to.isBlank()) {
      throw new IllegalArgumentException("Recipient address is required");
    }
    if (subject == null || subject.isBlank()) {
      throw new IllegalArgumentException("Subject is required");
    }
    if (htmlBody == null || htmlBody.isBlank()) {
      throw new IllegalArgumentException("Body is required");
    }
    // Guards against header injection through a crafted address or subject.
    if (containsLineBreak(to) || containsLineBreak(subject)) {
      throw new IllegalArgumentException("Recipient and subject must not contain line breaks");
    }
  }

  private static boolean containsLineBreak(String value) {
    return value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0;
  }
}
