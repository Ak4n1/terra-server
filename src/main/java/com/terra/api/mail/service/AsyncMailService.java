package com.terra.api.mail.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class AsyncMailService {
    private static final Logger log = LoggerFactory.getLogger(AsyncMailService.class);

    private final MailSenderService mailSenderService;

    public AsyncMailService(MailSenderService mailSenderService) {
        this.mailSenderService = mailSenderService;
    }

    @Async("mailTaskExecutor")
    public void sendHtml(String to, String subject, String htmlBody) {
        log.info("Queueing email delivery to={} subject={}", to, subject);
        try {
            mailSenderService.sendHtml(to, subject, htmlBody);
            log.info("Email delivered to={} subject={}", to, subject);
        } catch (RuntimeException exception) {
            log.error("Email delivery failed to={} subject={}", to, subject, exception);
            throw exception;
        }
    }
}
