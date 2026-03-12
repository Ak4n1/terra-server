package com.terra.api.mail.service;

import com.terra.api.common.i18n.message.MessageResolver;
import com.terra.api.common.i18n.model.SupportedLanguage;
import com.terra.api.common.i18n.resolver.CurrentLanguageResolver;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class EmailTemplateServiceTest {

    @Test
    void shouldBuildLocalizedVerificationEmailBodyWithFrontendLinkAndRecipient() {
        CurrentLanguageResolver currentLanguageResolver = new CurrentLanguageResolver();
        MessageResolver messageResolver = new MessageResolver(currentLanguageResolver);
        EmailTemplateService emailTemplateService = new EmailTemplateService(messageResolver);
        String verificationUrl = "https://l2terra.online/verify-email?token=abc123";

        EmailMessage emailMessage = emailTemplateService.buildEmailVerificationMessage(
                "user@l2terra.online",
                verificationUrl,
                SupportedLanguage.ES,
                15
        );

        assertTrue(emailMessage.subject().contains("Verifica"));
        assertTrue(emailMessage.htmlBody().contains("user@l2terra.online"));
        assertTrue(emailMessage.htmlBody().contains(verificationUrl));
        assertTrue(emailMessage.htmlBody().contains("https://l2terra.online/verify-email"));
        assertTrue(emailMessage.htmlBody().contains("Verificar email"));
        assertTrue(emailMessage.htmlBody().contains("L2 Terra"));
    }
}
