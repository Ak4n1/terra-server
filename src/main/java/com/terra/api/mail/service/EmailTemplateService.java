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
                verificationUrl,
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
                resetUrl,
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
                                   String fallbackUrl,
                                   String footerMessage) {
        StringBuilder noteMarkup = new StringBuilder();
        for (String note : notes) {
            noteMarkup.append("<p style=\"margin: 0; color: rgba(255, 255, 255, 0.72); font-family: Arial, Helvetica, sans-serif; font-size: 15px; line-height: 1.65;\">")
                    .append(escapeHtml(note))
                    .append("</p>");
        }

        return new StringBuilder()
                .append("<div style=\"margin: 0; padding: 32px 16px; background: #ffffff; color: #ffffff;\">")
                .append("<div style=\"max-width: 640px; margin: 0 auto; padding: 18px; background: #ffffff; border: 1px solid #d9d9d9; box-sizing: border-box;\">")
                .append("<div style=\"background: #0e0e0e; border: 1px solid #1d1d1d; box-shadow: 0 16px 40px rgba(0,0,0,0.24);\">")
                .append("<div style=\"padding: 24px 28px; border-top: 1px solid #000; border-bottom: 1px solid #000; background: rgba(255,255,255,0.02);\">")
                .append("<p style=\"margin: 0 0 10px; color: #e08821; font-family: Arial, Helvetica, sans-serif; font-size: 12px; font-weight: 700; letter-spacing: 0.16em; text-transform: uppercase;\">")
                .append(escapeHtml(eyebrow))
                .append("</p>")
                .append("<h2 style=\"margin: 0; color: #ffffff; font-family: Arial, Helvetica, sans-serif; font-size: 30px; font-weight: 700; line-height: 1.2;\">")
                .append(escapeHtml(title))
                .append("</h2>")
                .append("</div>")
                .append("<div style=\"padding: 30px 28px;\">")
                .append("<p style=\"margin: 0; color: rgba(255, 255, 255, 0.72); font-family: Arial, Helvetica, sans-serif; font-size: 15px; line-height: 1.65;\">")
                .append(escapeHtml(greeting))
                .append(" <strong style=\"color: #ffffff; font-weight: 600;\">")
                .append(escapeHtml(email))
                .append("</strong>,</p>")
                .append("<p style=\"margin: 18px 0 0; color: rgba(255, 255, 255, 0.72); font-family: Arial, Helvetica, sans-serif; font-size: 15px; line-height: 1.65;\">")
                .append(escapeHtml(body))
                .append("</p>")
                .append("<div style=\"padding: 20px 0 12px; text-align: center;\">")
                .append("<a href=\"")
                .append(actionUrl)
                .append("\" style=\"display: inline-block; position: relative; border: 0; padding: 16px 40px; background-color: rgba(14, 14, 14, 0.9); color: #e08821; font-family: Arial, Helvetica, sans-serif; font-size: 16px; font-weight: 700; line-height: 1.2; text-align: center; text-decoration: none; box-sizing: border-box;\">")
                .append("<span style=\"position: absolute; inset: 4px; border: 1px solid #b56d19;\"></span>")
                .append("<span style=\"position: relative; z-index: 1;\">")
                .append(escapeHtml(actionLabel))
                .append("</span>")
                .append("</a>")
                .append("</div>")
                .append("<div style=\"display: grid; gap: 8px; padding-top: 4px;\">")
                .append(noteMarkup)
                .append("</div>")
                .append("</div>")
                .append("<div style=\"display: grid; gap: 8px; padding: 24px 28px; border-top: 1px solid #000; border-bottom: 1px solid #000;\">")
                .append("<p style=\"margin: 0; color: rgba(255, 255, 255, 0.54); font-family: Arial, Helvetica, sans-serif; font-size: 13px; line-height: 1.65; word-break: break-all;\">")
                .append(escapeHtml(fallbackUrl))
                .append("</p>")
                .append("<p style=\"margin: 0; color: rgba(255, 255, 255, 0.52); font-family: Arial, Helvetica, sans-serif; font-size: 12px; line-height: 1.65;\">")
                .append(escapeHtml(footerMessage))
                .append("</p>")
                .append("</div>")
                .append("</div>")
                .append("</div>")
                .append("</div>")
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
