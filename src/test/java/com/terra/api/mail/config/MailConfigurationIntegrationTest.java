package com.terra.api.mail.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

@SpringBootTest(properties = {
        "spring.mail.host=smtp.hostinger.com",
        "spring.mail.port=465",
        "spring.mail.username=admin@l2terra.online",
        "spring.mail.password=test-password",
        "spring.mail.properties.mail.smtp.auth=true",
        "spring.mail.properties.mail.smtp.ssl.enable=true",
        "spring.mail.properties.mail.smtp.starttls.enable=false",
        "spring.mail.properties.mail.smtp.connectiontimeout=10000",
        "spring.mail.properties.mail.smtp.timeout=10000",
        "spring.mail.properties.mail.smtp.writetimeout=10000",
        "app.mail.from=admin@l2terra.online",
        "app.mail.frontend-verify-url=https://l2terra.online/verify-email",
        "app.mail.frontend-reset-password-url=https://l2terra.online/reset-password"
})
@ActiveProfiles("test")
class MailConfigurationIntegrationTest {

    @Autowired
    private MailProperties mailProperties;

    @Autowired
    private JavaMailSender javaMailSender;

    @Test
    void shouldBindApplicationAndSmtpMailProperties() {
        assertEquals("admin@l2terra.online", mailProperties.getFrom());
        assertEquals("https://l2terra.online/verify-email", mailProperties.getFrontendVerifyUrl());
        assertEquals("https://l2terra.online/reset-password", mailProperties.getFrontendResetPasswordUrl());

        JavaMailSenderImpl mailSender = assertInstanceOf(JavaMailSenderImpl.class, javaMailSender);
        assertEquals("smtp.hostinger.com", mailSender.getHost());
        assertEquals(465, mailSender.getPort());
        assertEquals("admin@l2terra.online", mailSender.getUsername());
        assertEquals("test-password", mailSender.getPassword());
        assertEquals("true", mailSender.getJavaMailProperties().getProperty("mail.smtp.auth"));
        assertEquals("true", mailSender.getJavaMailProperties().getProperty("mail.smtp.ssl.enable"));
        assertEquals("false", mailSender.getJavaMailProperties().getProperty("mail.smtp.starttls.enable"));
        assertEquals("10000", mailSender.getJavaMailProperties().getProperty("mail.smtp.connectiontimeout"));
        assertEquals("10000", mailSender.getJavaMailProperties().getProperty("mail.smtp.timeout"));
        assertEquals("10000", mailSender.getJavaMailProperties().getProperty("mail.smtp.writetimeout"));
    }
}
