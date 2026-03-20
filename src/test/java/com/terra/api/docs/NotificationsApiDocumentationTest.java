package com.terra.api.docs;

import com.terra.api.auth.domain.model.AccountMaster;
import com.terra.api.auth.domain.model.RoleName;
import com.terra.api.notifications.domain.model.AccountNotification;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.restdocs.cookies.CookieDocumentation;
import org.springframework.restdocs.headers.HeaderDocumentation;
import org.springframework.restdocs.request.RequestDocumentation;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.restdocs.cookies.CookieDocumentation.cookieWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class NotificationsApiDocumentationTest extends ApiDocumentationSupport {

    @Test
    void documentListNotifications() throws Exception {
        AccountMaster account = createVerifiedUser("docs-notifications@l2terra.online", RoleName.USER);
        notificationCommandService.createTestNotification(account);
        SessionCookies cookies = sessionCookiesFrom(login("docs-notifications@l2terra.online", "docs-notifications-login"));

        mockMvc.perform(get("/api/notifications")
                        .cookie(cookies.accessCookie())
                        .queryParam("limit", "10")
                        .queryParam("page", "0")
                        .queryParam("unreadOnly", "true"))
                .andExpect(status().isOk())
                .andDo(document("notifications/list",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        CookieDocumentation.requestCookies(
                                cookieWithName("terra_access_token").description("JWT de acceso del usuario autenticado.")
                        ),
                        RequestDocumentation.queryParameters(
                                parameterWithName("limit").description("Cantidad maxima de notificaciones a devolver."),
                                parameterWithName("page").description("Indice de pagina a consultar."),
                                parameterWithName("unreadOnly").description("Filtra solo notificaciones no leidas.")
                        )));
    }

    @Test
    void documentMarkRead() throws Exception {
        AccountMaster account = createVerifiedUser("docs-mark-read@l2terra.online", RoleName.USER);
        notificationCommandService.createTestNotification(account);
        AccountNotification notification = accountNotificationRepository.findAll().get(0);
        SessionCookies cookies = sessionCookiesFrom(login("docs-mark-read@l2terra.online", "docs-mark-read-login"));

        mockMvc.perform(patch("/api/notifications/{notificationId}/read", notification.getNotificationId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(cookies.accessCookie(), cookies.refreshCookie(), cookies.csrfCookie())
                        .header("X-CSRF-TOKEN", cookies.csrfToken())
                        .content("{}"))
                .andExpect(status().isOk())
                .andDo(document("notifications/mark-read",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        CookieDocumentation.requestCookies(
                                cookieWithName("terra_access_token").description("JWT de acceso actual."),
                                cookieWithName("terra_refresh_token").description("Refresh token actual."),
                                cookieWithName("XSRF-TOKEN").description("Cookie CSRF actual.")
                        ),
                        HeaderDocumentation.requestHeaders(
                                headerWithName("X-CSRF-TOKEN").description("Header CSRF requerido.")
                        ),
                        RequestDocumentation.pathParameters(
                                parameterWithName("notificationId").description("Identificador publico de la notificacion a marcar.")
                        )));
    }

    @Test
    void documentMarkAllRead() throws Exception {
        AccountMaster account = createVerifiedUser("docs-read-all@l2terra.online", RoleName.USER);
        notificationCommandService.createTestNotification(account);
        notificationCommandService.createTestNotification(account);
        SessionCookies cookies = sessionCookiesFrom(login("docs-read-all@l2terra.online", "docs-read-all-login"));

        mockMvc.perform(patch("/api/notifications/read-all")
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(cookies.accessCookie(), cookies.refreshCookie(), cookies.csrfCookie())
                        .header("X-CSRF-TOKEN", cookies.csrfToken())
                        .content("{}"))
                .andExpect(status().isOk())
                .andDo(document("notifications/mark-all-read",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        CookieDocumentation.requestCookies(
                                cookieWithName("terra_access_token").description("JWT de acceso actual."),
                                cookieWithName("terra_refresh_token").description("Refresh token actual."),
                                cookieWithName("XSRF-TOKEN").description("Cookie CSRF actual.")
                        ),
                        HeaderDocumentation.requestHeaders(
                                headerWithName("X-CSRF-TOKEN").description("Header CSRF requerido.")
                        )));
    }

    @Test
    void documentAdminTemplates() throws Exception {
        createVerifiedUser("docs-admin@l2terra.online", RoleName.ADMIN);
        SessionCookies cookies = sessionCookiesFrom(login("docs-admin@l2terra.online", "docs-admin-templates"));

        mockMvc.perform(get("/api/admin/notifications/templates")
                        .cookie(cookies.accessCookie()))
                .andExpect(status().isOk())
                .andDo(document("notifications/admin-templates",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        CookieDocumentation.requestCookies(
                                cookieWithName("terra_access_token").description("JWT de acceso de una cuenta administradora.")
                        )));
    }

    @Test
    void documentAdminAudit() throws Exception {
        createVerifiedUser("docs-admin-audit@l2terra.online", RoleName.ADMIN);
        createVerifiedUser("docs-target-audit@l2terra.online", RoleName.USER);
        SessionCookies cookies = sessionCookiesFrom(login("docs-admin-audit@l2terra.online", "docs-admin-audit-login"));

        mockMvc.perform(post("/api/admin/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(cookies.accessCookie(), cookies.refreshCookie(), cookies.csrfCookie())
                        .header("X-CSRF-TOKEN", cookies.csrfToken())
                        .content("""
                                {
                                  "email": "docs-target-audit@l2terra.online",
                                  "template": "system.admin_test_notification",
                                  "params": {}
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/notifications/audit")
                        .cookie(cookies.accessCookie())
                        .queryParam("page", "0")
                        .queryParam("size", "4")
                        .queryParam("template", "system.admin_test_notification")
                        .queryParam("status", "UNREAD")
                        .queryParam("dateFrom", "2026-03-01")
                        .queryParam("dateTo", "2026-03-31"))
                .andExpect(status().isOk())
                .andDo(document("notifications/admin-audit",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        CookieDocumentation.requestCookies(
                                cookieWithName("terra_access_token").description("JWT de acceso de una cuenta administradora.")
                        ),
                        RequestDocumentation.queryParameters(
                                parameterWithName("page").description("Indice de pagina a consultar."),
                                parameterWithName("size").description("Cantidad maxima de items por pagina."),
                                parameterWithName("template").description("Filtro opcional por codigo de plantilla."),
                                parameterWithName("status").description("Filtro opcional por estado de lectura."),
                                parameterWithName("dateFrom").description("Fecha inicial opcional en formato ISO local."),
                                parameterWithName("dateTo").description("Fecha final opcional en formato ISO local.")
                        )));
    }

    @Test
    void documentAdminDispatch() throws Exception {
        createVerifiedUser("docs-admin-dispatch@l2terra.online", RoleName.ADMIN);
        createVerifiedUser("docs-target-dispatch@l2terra.online", RoleName.USER);
        SessionCookies cookies = sessionCookiesFrom(login("docs-admin-dispatch@l2terra.online", "docs-admin-dispatch-login"));

        mockMvc.perform(post("/api/admin/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(cookies.accessCookie(), cookies.refreshCookie(), cookies.csrfCookie())
                        .header("X-CSRF-TOKEN", cookies.csrfToken())
                        .content("""
                                {
                                  "email": "docs-target-dispatch@l2terra.online",
                                  "template": "account.contact_support",
                                  "params": {
                                    "channelLabel": "Discord",
                                    "url": "https://discord.gg/terra-support"
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andDo(document("notifications/admin-dispatch",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        CookieDocumentation.requestCookies(
                                cookieWithName("terra_access_token").description("JWT de acceso de una cuenta administradora."),
                                cookieWithName("terra_refresh_token").description("Refresh token actual."),
                                cookieWithName("XSRF-TOKEN").description("Cookie CSRF actual.")
                        ),
                        HeaderDocumentation.requestHeaders(
                                headerWithName("X-CSRF-TOKEN").description("Header CSRF requerido.")
                        ),
                        requestFields(
                                fieldWithPath("email").description("Email del destinatario individual."),
                                fieldWithPath("template").description("Codigo de plantilla administrativa a emitir."),
                                fieldWithPath("params").description("Mapa de parametros dinamicos de la plantilla."),
                                fieldWithPath("params.channelLabel").description("Etiqueta visible del canal de soporte."),
                                fieldWithPath("params.url").description("URL externa que se mostrara como accion.")
                        )));
    }

    @Test
    void documentAdminBroadcast() throws Exception {
        createVerifiedUser("docs-admin-broadcast@l2terra.online", RoleName.ADMIN);
        createVerifiedUser("docs-user-a@l2terra.online", RoleName.USER);
        createVerifiedUser("docs-user-b@l2terra.online", RoleName.USER);
        SessionCookies cookies = sessionCookiesFrom(login("docs-admin-broadcast@l2terra.online", "docs-admin-broadcast-login"));

        mockMvc.perform(post("/api/admin/notifications/broadcast")
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(cookies.accessCookie(), cookies.refreshCookie(), cookies.csrfCookie())
                        .header("X-CSRF-TOKEN", cookies.csrfToken())
                        .content("""
                                {
                                  "template": "system.admin_test_notification",
                                  "params": {},
                                  "targetType": "ROLE",
                                  "targetValue": "USER"
                                }
                                """))
                .andExpect(status().isOk())
                .andDo(document("notifications/admin-broadcast",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        CookieDocumentation.requestCookies(
                                cookieWithName("terra_access_token").description("JWT de acceso de una cuenta administradora."),
                                cookieWithName("terra_refresh_token").description("Refresh token actual."),
                                cookieWithName("XSRF-TOKEN").description("Cookie CSRF actual.")
                        ),
                        HeaderDocumentation.requestHeaders(
                                headerWithName("X-CSRF-TOKEN").description("Header CSRF requerido.")
                        ),
                        requestFields(
                                fieldWithPath("template").description("Codigo de plantilla administrativa a emitir."),
                                fieldWithPath("params").description("Mapa de parametros dinamicos de la plantilla."),
                                fieldWithPath("targetType").description("Tipo de segmentacion del broadcast."),
                                fieldWithPath("targetValue").description("Valor concreto del segmento objetivo.")
                        )));
    }

    @Test
    void documentTestDispatch() throws Exception {
        createVerifiedUser("docs-test-dispatch@l2terra.online", RoleName.USER);

        mockMvc.perform(post("/api/test/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "docs-test-dispatch@l2terra.online",
                                  "template": "system.test_notification",
                                  "params": {}
                                }
                                """))
                .andExpect(status().isOk())
                .andDo(document("notifications/test-dispatch",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        requestFields(
                                fieldWithPath("email").description("Email del destinatario de la notificacion de prueba."),
                                fieldWithPath("template").description("Plantilla de desarrollo permitida."),
                                fieldWithPath("params").description("Mapa de parametros dinamicos.")
                        )));
    }

    private MvcResult login(String email, String seed) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(request -> {
                            request.setRemoteAddr(ipFor(seed));
                            return request;
                        })
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "%s"
                                }
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
    }
}
