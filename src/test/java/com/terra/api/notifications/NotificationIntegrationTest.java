package com.terra.api.notifications;

import com.terra.api.auth.entity.AccountMaster;
import com.terra.api.auth.entity.Role;
import com.terra.api.auth.entity.RoleName;
import com.terra.api.auth.repository.AccountMasterRepository;
import com.terra.api.auth.repository.AccountSessionRepository;
import com.terra.api.auth.repository.AccountVerificationRepository;
import com.terra.api.auth.repository.RoleRepository;
import com.terra.api.notifications.domain.AccountNotification;
import com.terra.api.notifications.domain.NotificationStatus;
import com.terra.api.notifications.repository.AccountNotificationRepository;
import com.terra.api.notifications.service.NotificationCommandService;
import com.terra.api.notifications.template.NotificationTemplateCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class NotificationIntegrationTest {

    private static final String PASSWORD = "Password1!";

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private AccountNotificationRepository accountNotificationRepository;

    @Autowired
    private AccountSessionRepository accountSessionRepository;

    @Autowired
    private AccountVerificationRepository accountVerificationRepository;

    @Autowired
    private AccountMasterRepository accountMasterRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private NotificationCommandService notificationCommandService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
        accountNotificationRepository.deleteAll();
        accountSessionRepository.deleteAll();
        accountVerificationRepository.deleteAll();
        accountMasterRepository.deleteAll();
    }

    // Verifica que el endpoint publico de prueba cree una notificacion persistida para la cuenta indicada y actualice unreadCount.
    @Test
    void shouldCreateTestNotificationThroughPublicDevEndpoint() throws Exception {
        createVerifiedUser("notify-test@l2terra.online");

        mockMvc.perform(post("/api/test/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "notify-test@l2terra.online",
                                  "template": "system.test_notification",
                                  "params": {}
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("notifications.test_dispatch_success"))
                .andExpect(jsonPath("$.data.unreadCount").value(1))
                .andExpect(jsonPath("$.data.notification.type").value("system.test_notification"));

        assertEquals(1, accountNotificationRepository.count());
        assertEquals(NotificationStatus.UNREAD, accountNotificationRepository.findAll().get(0).getStatus());
    }

    // Verifica que el endpoint publico de prueba rechace plantillas fuera del catalogo permitido para debug.
    @Test
    void shouldRejectUnsupportedTemplateInPublicDevEndpoint() throws Exception {
        createVerifiedUser("notify-test-invalid@l2terra.online");

        mockMvc.perform(post("/api/test/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "notify-test-invalid@l2terra.online",
                                  "template": "account.welcome_registered",
                                  "params": {}
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("notifications.test_template_not_allowed"));
    }

    // Verifica que el listado solo exponga las notificaciones de la cuenta autenticada aunque existan otras cuentas con datos propios.
    @Test
    void shouldListNotificationsOnlyForAuthenticatedAccount() throws Exception {
        AccountMaster firstAccount = createVerifiedUser("notify-list-a@l2terra.online");
        AccountMaster secondAccount = createVerifiedUser("notify-list-b@l2terra.online");
        notificationCommandService.createTestNotification(firstAccount);
        notificationCommandService.createTestNotification(secondAccount);
        SessionCookies cookies = login("notify-list-a@l2terra.online");

        mockMvc.perform(get("/api/notifications?limit=10")
                        .cookie(cookies.accessCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].type").value("system.test_notification"))
                .andExpect(jsonPath("$.data.unreadCount").value(1))
                .andExpect(jsonPath("$.data.hasMore").value(false))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(10));
    }

    // Verifica que el listado permita pedir solo notificaciones UNREAD paginadas de a 3 desde backend.
    @Test
    void shouldListUnreadNotificationsPaginated() throws Exception {
        AccountMaster account = createVerifiedUser("notify-page@l2terra.online");
        for (int index = 0; index < 5; index++) {
            notificationCommandService.createTestNotification(account);
        }
        SessionCookies cookies = login("notify-page@l2terra.online");

        mockMvc.perform(get("/api/notifications?limit=3&page=0&unreadOnly=true")
                        .cookie(cookies.accessCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(3))
                .andExpect(jsonPath("$.data.unreadCount").value(5))
                .andExpect(jsonPath("$.data.hasMore").value(true))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(3));

        mockMvc.perform(get("/api/notifications?limit=3&page=1&unreadOnly=true")
                        .cookie(cookies.accessCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.unreadCount").value(5))
                .andExpect(jsonPath("$.data.hasMore").value(false))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(3));
    }

    // Verifica que mark-read cambie el estado a READ y siga siendo idempotente cuando se ejecuta mas de una vez.
    @Test
    void shouldMarkNotificationAsReadIdempotently() throws Exception {
        AccountMaster account = createVerifiedUser("notify-read@l2terra.online");
        notificationCommandService.createTestNotification(account);
        AccountNotification notification = accountNotificationRepository.findAll().get(0);
        SessionCookies cookies = login("notify-read@l2terra.online");

        mockMvc.perform(patch("/api/notifications/{notificationId}/read", notification.getNotificationId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(cookies.accessCookie(), cookies.refreshCookie(), cookies.csrfCookie())
                        .header("X-CSRF-TOKEN", cookies.csrfToken())
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.notification.status").value("READ"))
                .andExpect(jsonPath("$.data.unreadCount").value(0));

        mockMvc.perform(patch("/api/notifications/{notificationId}/read", notification.getNotificationId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(cookies.accessCookie(), cookies.refreshCookie(), cookies.csrfCookie())
                        .header("X-CSRF-TOKEN", cookies.csrfToken())
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.notification.status").value("READ"))
                .andExpect(jsonPath("$.data.unreadCount").value(0));

        AccountNotification storedNotification = accountNotificationRepository.findAll().get(0);
        assertEquals(NotificationStatus.READ, storedNotification.getStatus());
        assertNotNull(storedNotification.getReadAt());
    }

    // Verifica que el endpoint read-all marque todas las UNREAD de la cuenta y deje unreadCount en cero.
    @Test
    void shouldMarkAllNotificationsAsRead() throws Exception {
        AccountMaster account = createVerifiedUser("notify-read-all@l2terra.online");
        notificationCommandService.createTestNotification(account);
        notificationCommandService.createTestNotification(account);
        SessionCookies cookies = login("notify-read-all@l2terra.online");

        mockMvc.perform(patch("/api/notifications/read-all")
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(cookies.accessCookie(), cookies.refreshCookie(), cookies.csrfCookie())
                        .header("X-CSRF-TOKEN", cookies.csrfToken())
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("notifications.mark_all_read_success"))
                .andExpect(jsonPath("$.data.unreadCount").value(0))
                .andExpect(jsonPath("$.data.updatedCount").value(2));

        assertEquals(2, accountNotificationRepository.findAll().stream()
                .filter(notification -> notification.getStatus() == NotificationStatus.READ)
                .count());
    }

    // Verifica que el registro exitoso cree la notificacion de bienvenida una sola vez para la nueva cuenta.
    @Test
    void shouldCreateWelcomeNotificationAfterRegister() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "notify-register@l2terra.online",
                                  "password": "%s"
                                }
                                """.formatted(PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("auth.verification_email_sent"));

        assertEquals(1, accountNotificationRepository.count());
        AccountNotification notification = accountNotificationRepository.findAll().get(0);
        assertEquals("account.welcome_registered", notification.getType());
        assertEquals("welcome_registered:" + notification.getAccount().getId(), notification.getDedupeKey());
    }

    // Verifica que la bienvenida no se duplique si el servicio intenta crearla mas de una vez para la misma cuenta.
    @Test
    void shouldNotDuplicateWelcomeNotificationWhenCreationIsRetried() {
        AccountMaster account = createVerifiedUser("notify-welcome@l2terra.online");

        notificationCommandService.createWelcomeRegistered(account);
        notificationCommandService.createWelcomeRegistered(account);

        assertEquals(1, accountNotificationRepository.count());
        assertEquals("account.welcome_registered", accountNotificationRepository.findAll().get(0).getType());
    }

    // Verifica que crear desde plantilla persista los datos base para una plantilla administrativa soportada.
    @Test
    void shouldCreateNotificationFromTemplate() {
        AccountMaster account = createVerifiedUser("notify-template@l2terra.online");

        notificationCommandService.createFromTemplate(account, NotificationTemplateCode.SYSTEM_MAINTENANCE_SCHEDULED, java.util.Map.of(
                "date", "2026-03-20",
                "time", "23:00",
                "timezone", "ART"
        ));

        assertEquals(1, accountNotificationRepository.count());
        AccountNotification notification = accountNotificationRepository.findAll().get(0);
        assertEquals("system.maintenance_scheduled", notification.getType());
    }

    private AccountMaster createVerifiedUser(String email) {
        Role userRole = roleRepository.findByName(RoleName.USER)
                .orElseThrow(() -> new IllegalStateException("USER role not found"));

        AccountMaster accountMaster = new AccountMaster();
        accountMaster.setEmail(email);
        accountMaster.setPasswordHash(passwordEncoder.encode(PASSWORD));
        accountMaster.setEmailVerified(true);
        accountMaster.setEnabled(true);
        accountMaster.setRoles(Set.of(userRole));
        return accountMasterRepository.save(accountMaster);
    }

    private SessionCookies login(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "%s"
                                }
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();

        return SessionCookies.fromHeaders(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE));
    }

    private record SessionCookies(Cookie accessCookie, Cookie refreshCookie, Cookie csrfCookie, String csrfToken) {
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
}
