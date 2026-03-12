package com.terra.api.mail.service;

import com.terra.api.common.i18n.message.MessageResolver;
import com.terra.api.common.i18n.model.SupportedLanguage;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class EmailTemplateService {

    private static final String EMAIL_VERIFICATION_MODULE = "mail/email-verification";
    private static final String PASSWORD_RESET_MODULE = "mail/password-reset";

    private final MessageResolver messageResolver;

    public EmailTemplateService(MessageResolver messageResolver) {
        this.messageResolver = messageResolver;
    }

    public EmailMessage buildEmailVerificationMessage(String email,
                                                      String verificationUrl,
                                                      SupportedLanguage language,
                                                      long expirationMinutes) {
        String title = messageResolver.getFromModule(
                EMAIL_VERIFICATION_MODULE,
                "mail.email_verification.subject",
                language
        );
        String subject = buildSubject(
                EMAIL_VERIFICATION_MODULE,
                "mail.email_verification.subject_label",
                language
        );
        String htmlBody = buildMailLayout(
                messageResolver.getFromModule(
                        EMAIL_VERIFICATION_MODULE,
                        "mail.email_verification.eyebrow",
                        language
                ),
                title,
                messageResolver.getFromModule(EMAIL_VERIFICATION_MODULE, "mail.email_verification.greeting", language),
                email,
                messageResolver.getFromModule(EMAIL_VERIFICATION_MODULE, "mail.email_verification.body", language),
                verificationUrl,
                messageResolver.getFromModule(EMAIL_VERIFICATION_MODULE, "mail.email_verification.cta", language),
                List.of(
                        messageResolver.getFromModule(
                                EMAIL_VERIFICATION_MODULE,
                                "mail.email_verification.expiry",
                                language,
                                expirationMinutes
                        ),
                        messageResolver.getFromModule(EMAIL_VERIFICATION_MODULE, "mail.email_verification.ignore", language)
                ),
                messageResolver.getFromModule(EMAIL_VERIFICATION_MODULE, "mail.common.link_fallback", language),
                verificationUrl,
                messageResolver.getFromModule(EMAIL_VERIFICATION_MODULE, "mail.common.signature_closing", language),
                messageResolver.getFromModule(EMAIL_VERIFICATION_MODULE, "mail.common.signature_name", language),
                messageResolver.getFromModule(EMAIL_VERIFICATION_MODULE, "mail.email_verification.footer", language)
        );
        return new EmailMessage(subject, htmlBody);
    }

    public EmailMessage buildPasswordResetMessage(String email,
                                                  String resetUrl,
                                                  SupportedLanguage language,
                                                  long expirationMinutes) {
        String title = messageResolver.getFromModule(
                PASSWORD_RESET_MODULE,
                "mail.password_reset.subject",
                language
        );
        String subject = buildSubject(
                PASSWORD_RESET_MODULE,
                "mail.password_reset.subject_label",
                language
        );
        String htmlBody = buildMailLayout(
                messageResolver.getFromModule(
                        PASSWORD_RESET_MODULE,
                        "mail.password_reset.eyebrow",
                        language
                ),
                title,
                messageResolver.getFromModule(PASSWORD_RESET_MODULE, "mail.password_reset.greeting", language),
                email,
                messageResolver.getFromModule(PASSWORD_RESET_MODULE, "mail.password_reset.body", language),
                resetUrl,
                messageResolver.getFromModule(PASSWORD_RESET_MODULE, "mail.password_reset.cta", language),
                List.of(
                        messageResolver.getFromModule(
                                PASSWORD_RESET_MODULE,
                                "mail.password_reset.expiry",
                                language,
                                expirationMinutes
                        ),
                        messageResolver.getFromModule(PASSWORD_RESET_MODULE, "mail.password_reset.ignore", language)
                ),
                messageResolver.getFromModule(PASSWORD_RESET_MODULE, "mail.common.link_fallback", language),
                resetUrl,
                messageResolver.getFromModule(PASSWORD_RESET_MODULE, "mail.common.signature_closing", language),
                messageResolver.getFromModule(PASSWORD_RESET_MODULE, "mail.common.signature_name", language),
                messageResolver.getFromModule(PASSWORD_RESET_MODULE, "mail.password_reset.footer", language)
        );
        return new EmailMessage(subject, htmlBody);
    }

    private String buildSubject(String module, String subjectLabelKey, SupportedLanguage language) {
        String brand = messageResolver.getFromModule(module, "mail.common.brand", language);
        String subjectLabel = messageResolver.getFromModule(module, subjectLabelKey, language);
        return brand + " - " + subjectLabel;
    }

    private String buildMailLayout(String eyebrow,
                                   String title,
                                   String greeting,
                                   String email,
                                   String body,
                                   String actionUrl,
                                   String actionLabel,
                                   List<String> notes,
                                   String fallbackLabel,
                                   String fallbackUrl,
                                   String signatureClosing,
                                   String signatureName,
                                   String footerMessage) {
        StringBuilder noteMarkup = new StringBuilder();
        for (String note : notes) {
            noteMarkup.append("<p style=\"margin: 0 0 12px; color: #525252; font-family: Arial, Helvetica, sans-serif; font-size: 14px; line-height: 1.7;\">")
                    .append(escapeHtml(note))
                    .append("</p>");
        }

        return new StringBuilder()
                .append("<!DOCTYPE html>")
                .append("<html lang=\"en\">")
                .append("<body style=\"margin: 0; padding: 0; background-color: #ffffff;\">")
                .append("<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" width=\"100%\" style=\"width: 100%; border-collapse: collapse; background-color: #ffffff;\">")
                .append("<tr>")
                .append("<td align=\"center\" style=\"padding: 32px 16px;\">")
                .append("<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" width=\"100%\" style=\"width: 100%; max-width: 720px; border-collapse: collapse;\">")
                .append("<tr>")
                .append("<td style=\"padding: 0 0 18px; font-family: Arial, Helvetica, sans-serif; font-size: 34px; font-weight: 700; line-height: 1; letter-spacing: 0.02em; color: #111111; text-transform: uppercase;\">")
                .append("<span style=\"color: #e08821;\">L2</span> Terra")
                .append("</td>")
                .append("</tr>")
                .append("<tr>")
                .append("<td style=\"border: 1px solid #3a3a3a; background-color: #131313; padding: 0;\">")
                .append("<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" width=\"100%\" style=\"width: 100%; border-collapse: collapse;\">")
                .append("<tr>")
                .append("<td style=\"padding: 30px 32px 18px; border-bottom: 1px solid #2a2a2a;\">")
                .append("<p style=\"margin: 0 0 10px; color: #e08821; font-family: Arial, Helvetica, sans-serif; font-size: 12px; font-weight: 700; letter-spacing: 0.16em; text-transform: uppercase;\">")
                .append(escapeHtml(eyebrow))
                .append("</p>")
                .append("<h1 style=\"margin: 0; color: #ffffff; font-family: Arial, Helvetica, sans-serif; font-size: 30px; font-weight: 700; line-height: 1.2;\">")
                .append(escapeHtml(title))
                .append("</h1>")
                .append("</td>")
                .append("</tr>")
                .append("<tr>")
                .append("<td style=\"padding: 28px 32px 16px;\">")
                .append("<p style=\"margin: 0; color: #b8b8b8; font-family: Arial, Helvetica, sans-serif; font-size: 15px; line-height: 1.7;\">")
                .append(escapeHtml(greeting))
                .append(" <strong style=\"color: #ffffff; font-weight: 700;\">")
                .append(escapeHtml(email))
                .append("</strong>,</p>")
                .append("<p style=\"margin: 18px 0 0; color: #b8b8b8; font-family: Arial, Helvetica, sans-serif; font-size: 15px; line-height: 1.7;\">")
                .append(escapeHtml(body))
                .append("</p>")
                .append("</td>")
                .append("</tr>")
                .append("<tr>")
                .append("<td align=\"center\" style=\"padding: 8px 32px 24px;\">")
                .append("<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" style=\"border-collapse: collapse;\">")
                .append("<tr>")
                .append("<td align=\"center\" style=\"border: 1px solid #b56d19; background-color: #131313;\">")
                .append("<a href=\"")
                .append(actionUrl)
                .append("\" style=\"display: inline-block; padding: 14px 28px; color: #e08821; font-family: Arial, Helvetica, sans-serif; font-size: 14px; font-weight: 700; line-height: 1.2; text-align: center; text-decoration: none; text-transform: uppercase; letter-spacing: 0.08em;\">")
                .append(escapeHtml(actionLabel))
                .append("</a>")
                .append("</td>")
                .append("</tr>")
                .append("</table>")
                .append("</td>")
                .append("</tr>")
                .append("<tr>")
                .append("<td style=\"padding: 0 32px 8px;\">")
                .append(noteMarkup)
                .append("</td>")
                .append("</tr>")
                .append("<tr>")
                .append("<td style=\"padding: 8px 32px 0;\">")
                .append("<p style=\"margin: 0 0 6px; color: #b8b8b8; font-family: Arial, Helvetica, sans-serif; font-size: 15px; line-height: 1.7;\">")
                .append(escapeHtml(signatureClosing))
                .append("</p>")
                .append("<p style=\"margin: 0 0 24px; color: #ffffff; font-family: Arial, Helvetica, sans-serif; font-size: 15px; font-weight: 700; line-height: 1.7;\">")
                .append(escapeHtml(signatureName))
                .append("</p>")
                .append("</td>")
                .append("</tr>")
                .append("<tr>")
                .append("<td style=\"padding: 18px 32px 28px; border-top: 1px solid #2a2a2a;\">")
                .append("<p style=\"margin: 0 0 10px; color: #b8b8b8; font-family: Arial, Helvetica, sans-serif; font-size: 13px; line-height: 1.65;\">")
                .append(escapeHtml(fallbackLabel))
                .append("</p>")
                .append("<p style=\"margin: 0 0 12px; color: #9c9c9c; font-family: Arial, Helvetica, sans-serif; font-size: 13px; line-height: 1.65; word-break: break-all;\">")
                .append(escapeHtml(fallbackUrl))
                .append("</p>")
                .append("<p style=\"margin: 0; color: #8a8a8a; font-family: Arial, Helvetica, sans-serif; font-size: 12px; line-height: 1.65;\">")
                .append(escapeHtml(footerMessage))
                .append("</p>")
                .append("</td>")
                .append("</tr>")
                .append("</table>")
                .append("</td>")
                .append("</tr>")
                .append("</table>")
                .append("</td>")
                .append("</tr>")
                .append("</table>")
                .append("</body>")
                .append("</html>")
                .toString();
    }

    private String escapeHtml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
