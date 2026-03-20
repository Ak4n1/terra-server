package com.terra.api.game.accounts.infrastructure.mail;

import com.terra.api.common.domain.i18n.SupportedLanguage;
import com.terra.api.common.infrastructure.i18n.CurrentLanguageResolver;
import com.terra.api.mail.application.EmailTemplateService;
import com.terra.api.mail.application.AsyncMailService;
import com.terra.api.mail.domain.EmailMessage;
import com.terra.api.mail.infrastructure.config.MailProperties;
import org.springframework.stereotype.Service;

@Service
public class GameAccountEmailTemplateService {

    private final AsyncMailService asyncMailService;
    private final EmailTemplateService emailTemplateService;
    private final CurrentLanguageResolver currentLanguageResolver;
    private final MailProperties mailProperties;

    public GameAccountEmailTemplateService(AsyncMailService asyncMailService,
                                           EmailTemplateService emailTemplateService,
                                           CurrentLanguageResolver currentLanguageResolver,
                                           MailProperties mailProperties) {
        this.asyncMailService = asyncMailService;
        this.emailTemplateService = emailTemplateService;
        this.currentLanguageResolver = currentLanguageResolver;
        this.mailProperties = mailProperties;
    }

    public void sendCreationCodeEmail(String email, String code, long expiresInMinutes) {
        SupportedLanguage language = currentLanguageResolver.resolve();
        EmailMessage emailMessage = emailTemplateService.buildGameAccountCreationCodeMessage(
                email,
                code,
                mailProperties.getFrontendGameAccountsUrl(),
                language,
                expiresInMinutes
        );
        asyncMailService.sendHtml(email, emailMessage.subject(), emailMessage.htmlBody());
    }
}
