package com.terra.api.mail.service;

import com.terra.api.mail.config.MailProperties;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;

@Service
public class SmtpMailSenderService implements MailSenderService {

    private final JavaMailSender javaMailSender;
    private final MailProperties mailProperties;

    public SmtpMailSenderService(JavaMailSender javaMailSender, MailProperties mailProperties) {
        this.javaMailSender = javaMailSender;
        this.mailProperties = mailProperties;
    }

    @Override
    public void sendHtml(String to, String subject, String htmlBody) {
        try {
            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            String fromName = mailProperties.getFromName();
            if (fromName == null || fromName.isBlank()) {
                helper.setFrom(mailProperties.getFrom());
            } else {
                helper.setFrom(new InternetAddress(mailProperties.getFrom(), fromName, "UTF-8"));
            }
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            javaMailSender.send(message);
        } catch (MessagingException | UnsupportedEncodingException exception) {
            throw new IllegalStateException("Failed to compose email", exception);
        }
    }
}
