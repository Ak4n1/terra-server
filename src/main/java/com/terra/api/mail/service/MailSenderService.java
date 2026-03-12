package com.terra.api.mail.service;

public interface MailSenderService {
    void sendHtml(String to, String subject, String htmlBody);
}
