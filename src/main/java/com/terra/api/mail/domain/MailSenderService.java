package com.terra.api.mail.domain;

public interface MailSenderService {
    void sendHtml(String to, String subject, String htmlBody);
}
