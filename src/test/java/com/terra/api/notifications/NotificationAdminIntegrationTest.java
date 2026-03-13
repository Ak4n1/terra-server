package com.terra.api.notifications;

import com.terra.api.auth.entity.AccountMaster;
import com.terra.api.auth.entity.Role;
import com.terra.api.auth.entity.RoleName;
import com.terra.api.auth.repository.AccountMasterRepository;
import com.terra.api.auth.repository.AccountSessionRepository;
import com.terra.api.auth.repository.AccountVerificationRepository;
import com.terra.api.auth.repository.RoleRepository;
import com.terra.api.notifications.repository.AccountNotificationRepository;
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
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class NotificationAdminIntegrationTest {

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

    // Verifica que un admin pueda listar solo las plantillas habilitadas para envios administrativos.
    @Test
    void shouldListAdminTemplatesForAdminAccount() throws Exception {
        createVerifiedUser("notify-admin@l2terra.online", RoleName.ADMIN);
        SessionCookies cookies = login("notify-admin@l2terra.online");

                mockMvc.perform(get("/api/admin/notifications/templates")
                        .cookie(cookies.accessCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("notifications.admin_templates_loaded"))
                .andExpect(jsonPath("$.data.length()").value(13))
                .andExpect(jsonPath("$.data[0].code").value("system.test_notification"))
                .andExpect(jsonPath("$.data[0].allowedTarget").value("BOTH"))
                .andExpect(jsonPath("$.data[3].code").value("admin.general_announcement"));
    }

    // Verifica que un admin pueda despachar una plantilla permitida y que la notificacion quede persistida.
    @Test
    void shouldDispatchAdminNotificationForAllowedTemplate() throws Exception {
        createVerifiedUser("notify-admin@l2terra.online", RoleName.ADMIN);
        createVerifiedUser("notify-target@l2terra.online", RoleName.USER);
        SessionCookies cookies = login("notify-admin@l2terra.online");

        mockMvc.perform(post("/api/admin/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(cookies.accessCookie(), cookies.refreshCookie(), cookies.csrfCookie())
                        .header("X-CSRF-TOKEN", cookies.csrfToken())
                        .content("""
                                {
                                  "email": "notify-target@l2terra.online",
                                  "template": "admin.contact_request",
                                  "params": {
                                    "message": "Necesitamos hablar con vos sobre tu cuenta."
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("notifications.admin_dispatch_success"))
                .andExpect(jsonPath("$.data.notification.type").value("admin.contact_request"))
                .andExpect(jsonPath("$.data.notification.params.message").value("Necesitamos hablar con vos sobre tu cuenta."));

        assertEquals(1, accountNotificationRepository.count());
    }

    // Verifica que una cuenta comun no pueda acceder al endpoint administrativo de notificaciones.
    @Test
    void shouldRejectAdminNotificationEndpointForUserAccount() throws Exception {
        createVerifiedUser("notify-user@l2terra.online", RoleName.USER);
        SessionCookies cookies = login("notify-user@l2terra.online");

        mockMvc.perform(get("/api/admin/notifications/templates")
                        .cookie(cookies.accessCookie()))
                .andExpect(status().isForbidden());
    }

    // Verifica que un admin pueda hacer broadcast por rol y que el sistema informe cuantas cuentas recibieron la notificacion.
    @Test
    void shouldBroadcastNotificationByRole() throws Exception {
        createVerifiedUser("notify-admin@l2terra.online", RoleName.ADMIN);
        createVerifiedUser("notify-user-a@l2terra.online", RoleName.USER);
        createVerifiedUser("notify-user-b@l2terra.online", RoleName.USER);
        SessionCookies cookies = login("notify-admin@l2terra.online");

        mockMvc.perform(post("/api/admin/notifications/broadcast")
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(cookies.accessCookie(), cookies.refreshCookie(), cookies.csrfCookie())
                        .header("X-CSRF-TOKEN", cookies.csrfToken())
                        .content("""
                                {
                                  "template": "system.security_review_required",
                                  "params": {
                                    "reason": "new_login_detected"
                                  },
                                  "targetType": "ROLE",
                                  "targetValue": "USER"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("notifications.admin_broadcast_success"))
                .andExpect(jsonPath("$.data.deliveredCount").value(2));
    }

    // Verifica que el backend rechace una plantilla solo individual cuando se intenta usar en broadcast.
    @Test
    void shouldRejectBroadcastForIndividualOnlyTemplate() throws Exception {
        createVerifiedUser("notify-admin@l2terra.online", RoleName.ADMIN);
        SessionCookies cookies = login("notify-admin@l2terra.online");

        mockMvc.perform(post("/api/admin/notifications/broadcast")
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(cookies.accessCookie(), cookies.refreshCookie(), cookies.csrfCookie())
                        .header("X-CSRF-TOKEN", cookies.csrfToken())
                        .content("""
                                {
                                  "template": "admin.contact_request",
                                  "params": {
                                    "message": "Necesitamos hablar con vos."
                                  },
                                  "targetType": "ROLE",
                                  "targetValue": "USER"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("notifications.admin_template_target_not_allowed"));
    }

    private AccountMaster createVerifiedUser(String email, RoleName roleName) {
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
