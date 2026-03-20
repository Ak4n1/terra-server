package com.terra.api.docs;

import com.terra.api.auth.domain.model.AccountMaster;
import com.terra.api.auth.domain.model.Role;
import com.terra.api.auth.domain.model.RoleName;
import com.terra.api.auth.infrastructure.persistence.AccountMasterRepository;
import com.terra.api.auth.infrastructure.persistence.AccountSessionRepository;
import com.terra.api.auth.infrastructure.persistence.AccountVerificationRepository;
import com.terra.api.auth.infrastructure.persistence.RoleRepository;
import com.terra.api.mail.domain.MailSenderService;
import com.terra.api.notifications.application.NotificationCommandService;
import com.terra.api.notifications.infrastructure.persistence.AccountNotificationRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.restdocs.RestDocumentationContextProvider;
import org.springframework.restdocs.RestDocumentationExtension;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

@SpringBootTest
@ActiveProfiles("test")
@ExtendWith({SpringExtension.class, RestDocumentationExtension.class})
@Import(ApiDocumentationSupport.TestMailConfig.class)
public abstract class ApiDocumentationSupport {

    protected static final String PASSWORD = "Password1!";
    private static final Pattern TOKEN_PATTERN = Pattern.compile("\\?token=([^\"']+)");

    protected MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    protected AccountNotificationRepository accountNotificationRepository;

    @Autowired
    protected AccountSessionRepository accountSessionRepository;

    @Autowired
    protected AccountVerificationRepository accountVerificationRepository;

    @Autowired
    protected AccountMasterRepository accountMasterRepository;

    @Autowired
    protected RoleRepository roleRepository;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @Autowired
    protected NotificationCommandService notificationCommandService;

    @Autowired
    protected CapturingMailSenderService capturingMailSenderService;

    @BeforeEach
    void cleanRepositories() {
        accountNotificationRepository.deleteAll();
        accountSessionRepository.deleteAll();
        accountVerificationRepository.deleteAll();
        accountMasterRepository.deleteAll();
        capturingMailSenderService.clear();
    }

    @BeforeEach
    void setUpMockMvc(RestDocumentationContextProvider restDocumentation) {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .apply(documentationConfiguration(restDocumentation)
                        .uris()
                        .withScheme("http")
                        .withHost("localhost")
                        .withPort(8080))
                .build();
    }

    protected AccountMaster createVerifiedUser(String email, RoleName roleName) {
        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new IllegalStateException(roleName + " role not found"));

        AccountMaster accountMaster = new AccountMaster();
        accountMaster.setEmail(email);
        accountMaster.setPasswordHash(passwordEncoder.encode(PASSWORD));
        accountMaster.setEmailVerified(true);
        accountMaster.setEnabled(true);
        accountMaster.setRoles(Set.of(role));
        return accountMasterRepository.save(accountMaster);
    }

    protected SessionCookies sessionCookiesFrom(MvcResult result) {
        return SessionCookies.fromHeaders(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE));
    }

    protected String latestEmailToken() {
        String htmlBody = capturingMailSenderService.lastBody();
        Matcher matcher = TOKEN_PATTERN.matcher(htmlBody);
        if (!matcher.find()) {
            throw new IllegalStateException("No se encontro token en el email capturado");
        }
        return matcher.group(1);
    }

    protected String ipFor(String seed) {
        int hash = Math.abs(seed.hashCode());
        int second = (hash % 200) + 1;
        int third = ((hash / 200) % 200) + 1;
        int fourth = ((hash / 40000) % 200) + 1;
        return "10.%d.%d.%d".formatted(second, third, fourth);
    }

    protected record SessionCookies(Cookie accessCookie, Cookie refreshCookie, Cookie csrfCookie, String csrfToken) {
        static SessionCookies fromHeaders(List<String> setCookieHeaders) {
            Cookie accessCookie = null;
            Cookie refreshCookie = null;
            Cookie csrfCookie = null;

            for (String headerValue : setCookieHeaders) {
                String[] parts = headerValue.split(";\\s*");
                String[] nameAndValue = parts[0].split("=", 2);
                if (nameAndValue.length < 2) {
                    continue;
                }

                switch (nameAndValue[0]) {
                    case "terra_access_token" -> accessCookie = new Cookie(nameAndValue[0], nameAndValue[1]);
                    case "terra_refresh_token" -> refreshCookie = new Cookie(nameAndValue[0], nameAndValue[1]);
                    case "XSRF-TOKEN" -> csrfCookie = new Cookie(nameAndValue[0], nameAndValue[1]);
                    default -> {
                    }
                }
            }

            if (accessCookie == null || refreshCookie == null || csrfCookie == null) {
                throw new IllegalStateException("Missing auth cookies after login");
            }

            return new SessionCookies(accessCookie, refreshCookie, csrfCookie, csrfCookie.getValue());
        }
    }

    @TestConfiguration
    static class TestMailConfig {

        @Bean
        @Primary
        CapturingMailSenderService mailSenderService() {
            return new CapturingMailSenderService();
        }
    }

    static class CapturingMailSenderService implements MailSenderService {

        private final AtomicReference<String> lastBody = new AtomicReference<>();

        @Override
        public void sendHtml(String to, String subject, String htmlBody) {
            lastBody.set(htmlBody);
        }

        String lastBody() {
            String body = lastBody.get();
            if (body == null) {
                throw new IllegalStateException("No email captured");
            }
            return body;
        }

        void clear() {
            lastBody.set(null);
        }
    }
}
