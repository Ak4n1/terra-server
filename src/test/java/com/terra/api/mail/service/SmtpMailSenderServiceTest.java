package com.terra.api.mail.service;

import com.terra.api.mail.config.MailProperties;
import jakarta.mail.Address;
import jakarta.mail.Message;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SmtpMailSenderServiceTest {

    @Test
    void shouldComposeAndSendHtmlEmailUsingConfiguredFromAddress() throws Exception {
        JavaMailSender javaMailSender = mock(JavaMailSender.class);
        MimeMessage mimeMessage = new MimeMessage(jakarta.mail.Session.getInstance(new Properties()));
        when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);

        MailProperties mailProperties = new MailProperties();
        mailProperties.setFrom("admin@l2terra.online");

        SmtpMailSenderService smtpMailSenderService = new SmtpMailSenderService(javaMailSender, mailProperties);

        smtpMailSenderService.sendHtml("player@l2terra.online", "Verify your L2 Terra account", "<strong>Hello</strong>");

        verify(javaMailSender).send(mimeMessage);
        Address[] fromAddresses = mimeMessage.getFrom();
        Address[] recipients = mimeMessage.getRecipients(Message.RecipientType.TO);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        mimeMessage.writeTo(outputStream);
        String rawMessage = outputStream.toString(StandardCharsets.UTF_8);

        assertEquals("admin@l2terra.online", ((InternetAddress) fromAddresses[0]).getAddress());
        assertEquals("player@l2terra.online", ((InternetAddress) recipients[0]).getAddress());
        assertEquals("Verify your L2 Terra account", mimeMessage.getSubject());
        assertTrue(rawMessage.contains("text/html"));
        assertTrue(rawMessage.contains("<strong>Hello</strong>"));
    }
}
