package com.terra.api.docs;

import com.terra.api.auth.domain.model.RoleName;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.restdocs.cookies.CookieDocumentation;
import org.springframework.restdocs.headers.HeaderDocumentation;

import static org.springframework.restdocs.cookies.CookieDocumentation.cookieWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthApiDocumentationTest extends ApiDocumentationSupport {

    @Test
    void documentConfig() throws Exception {
        mockMvc.perform(get("/api/auth/config"))
                .andExpect(status().isOk())
                .andDo(document("auth/config",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        responseFields(
                                fieldWithPath("code").description("Codigo interno de la respuesta."),
                                fieldWithPath("message").description("Mensaje legible para el cliente."),
                                fieldWithPath("data.csrfCookieName").description("Nombre de la cookie CSRF que usa el frontend."),
                                fieldWithPath("data.csrfHeaderName").description("Nombre del header CSRF que usa el frontend.")
                        )));
    }

    @Test
    void documentRegister() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(request -> {
                            request.setRemoteAddr(ipFor("docs-register"));
                            return request;
                        })
                        .content("""
                                {
                                  "email": "docs-register@l2terra.online",
                                  "password": "Password1!"
                                }
                                """))
                .andExpect(status().isCreated())
                .andDo(document("auth/register",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        requestFields(
                                fieldWithPath("email").description("Email unico de la cuenta a registrar."),
                                fieldWithPath("password").description("Contrasena inicial de la cuenta.")
                        )));
    }

    @Test
    void documentVerifyEmail() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(request -> {
                            request.setRemoteAddr(ipFor("docs-register-verify"));
                            return request;
                        })
                        .content("""
                                {
                                  "email": "docs-verify@l2terra.online",
                                  "password": "Password1!"
                                }
                                """))
                .andExpect(status().isCreated());

        String token = latestEmailToken();

        mockMvc.perform(post("/api/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(request -> {
                            request.setRemoteAddr(ipFor("docs-verify-email"));
                            return request;
                        })
                        .content("""
                                {
                                  "token": "%s"
                                }
                                """.formatted(token)))
                .andExpect(status().isOk())
                .andDo(document("auth/verify-email",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        requestFields(
                                fieldWithPath("token").description("Token de verificacion recibido por email.")
                        )));
    }

    @Test
    void documentResendVerification() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(request -> {
                            request.setRemoteAddr(ipFor("docs-register-resend"));
                            return request;
                        })
                        .content("""
                                {
                                  "email": "docs-resend@l2terra.online",
                                  "password": "Password1!"
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/resend-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(request -> {
                            request.setRemoteAddr(ipFor("docs-resend"));
                            return request;
                        })
                        .content("""
                                {
                                  "email": "docs-resend@l2terra.online"
                                }
                                """))
                .andExpect(status().isOk())
                .andDo(document("auth/resend-verification",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        requestFields(
                                fieldWithPath("email").description("Email al que se intentara reenviar la verificacion.")
                        )));
    }

    @Test
    void documentForgotPassword() throws Exception {
        createVerifiedUser("docs-forgot@l2terra.online", RoleName.USER);

        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(request -> {
                            request.setRemoteAddr(ipFor("docs-forgot"));
                            return request;
                        })
                        .content("""
                                {
                                  "email": "docs-forgot@l2terra.online"
                                }
                                """))
                .andExpect(status().isOk())
                .andDo(document("auth/forgot-password",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        requestFields(
                                fieldWithPath("email").description("Email al que se intentara enviar el enlace de recuperacion.")
                        )));
    }

    @Test
    void documentResetPassword() throws Exception {
        createVerifiedUser("docs-reset@l2terra.online", RoleName.USER);

        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(request -> {
                            request.setRemoteAddr(ipFor("docs-forgot-reset"));
                            return request;
                        })
                        .content("""
                                {
                                  "email": "docs-reset@l2terra.online"
                                }
                                """))
                .andExpect(status().isOk());

        String token = latestEmailToken();

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(request -> {
                            request.setRemoteAddr(ipFor("docs-reset-password"));
                            return request;
                        })
                        .content("""
                                {
                                  "token": "%s",
                                  "newPassword": "AnotherPass1!"
                                }
                                """.formatted(token)))
                .andExpect(status().isOk())
                .andDo(document("auth/reset-password",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        requestFields(
                                fieldWithPath("token").description("Token de recuperacion de contrasena."),
                                fieldWithPath("newPassword").description("Nueva contrasena a persistir.")
                        )));
    }

    @Test
    void documentLogin() throws Exception {
        createVerifiedUser("docs-login@l2terra.online", RoleName.USER);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(request -> {
                            request.setRemoteAddr(ipFor("docs-login"));
                            return request;
                        })
                        .content("""
                                {
                                  "email": "docs-login@l2terra.online",
                                  "password": "Password1!"
                                }
                                """))
                .andExpect(status().isOk())
                .andDo(document("auth/login",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        requestFields(
                                fieldWithPath("email").description("Email con el que se autentica el usuario."),
                                fieldWithPath("password").description("Contrasena del usuario.")
                        ),
                        CookieDocumentation.responseCookies(
                                cookieWithName("terra_access_token").description("JWT de acceso en cookie HttpOnly."),
                                cookieWithName("terra_refresh_token").description("JWT de refresh en cookie HttpOnly."),
                                cookieWithName("XSRF-TOKEN").description("Token CSRF para requests protegidas.")
                        )));
    }

    @Test
    void documentRefresh() throws Exception {
        createVerifiedUser("docs-refresh@l2terra.online", RoleName.USER);
        SessionCookies cookies = sessionCookiesFrom(login("docs-refresh@l2terra.online", "docs-refresh-login"));

        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(cookies.refreshCookie(), cookies.csrfCookie())
                        .header("X-CSRF-TOKEN", cookies.csrfToken()))
                .andExpect(status().isOk())
                .andDo(document("auth/refresh",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        CookieDocumentation.requestCookies(
                                cookieWithName("terra_refresh_token").description("Refresh token actual."),
                                cookieWithName("XSRF-TOKEN").description("Cookie CSRF actual.")
                        ),
                        HeaderDocumentation.requestHeaders(
                                headerWithName("X-CSRF-TOKEN").description("Header que debe coincidir con la cookie CSRF.")
                        )));
    }

    @Test
    void documentLogout() throws Exception {
        createVerifiedUser("docs-logout@l2terra.online", RoleName.USER);
        SessionCookies cookies = sessionCookiesFrom(login("docs-logout@l2terra.online", "docs-logout-login"));

        mockMvc.perform(post("/api/auth/logout")
                        .cookie(cookies.accessCookie(), cookies.refreshCookie(), cookies.csrfCookie())
                        .header("X-CSRF-TOKEN", cookies.csrfToken()))
                .andExpect(status().isOk())
                .andDo(document("auth/logout",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        CookieDocumentation.requestCookies(
                                cookieWithName("terra_access_token").description("JWT de acceso actual."),
                                cookieWithName("terra_refresh_token").description("Refresh token actual."),
                                cookieWithName("XSRF-TOKEN").description("Cookie CSRF actual.")
                        ),
                        HeaderDocumentation.requestHeaders(
                                headerWithName("X-CSRF-TOKEN").description("Header CSRF que valida la operacion.")
                        )));
    }

    @Test
    void documentLogoutAll() throws Exception {
        createVerifiedUser("docs-logout-all@l2terra.online", RoleName.USER);
        SessionCookies cookies = sessionCookiesFrom(login("docs-logout-all@l2terra.online", "docs-logout-all-login"));

        mockMvc.perform(post("/api/auth/logout-all")
                        .cookie(cookies.accessCookie(), cookies.refreshCookie(), cookies.csrfCookie())
                        .header("X-CSRF-TOKEN", cookies.csrfToken()))
                .andExpect(status().isOk())
                .andDo(document("auth/logout-all",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        CookieDocumentation.requestCookies(
                                cookieWithName("terra_access_token").description("JWT de acceso actual."),
                                cookieWithName("terra_refresh_token").description("Refresh token actual."),
                                cookieWithName("XSRF-TOKEN").description("Cookie CSRF actual.")
                        ),
                        HeaderDocumentation.requestHeaders(
                                headerWithName("X-CSRF-TOKEN").description("Header CSRF que valida la operacion.")
                        )));
    }

    @Test
    void documentCurrentUser() throws Exception {
        createVerifiedUser("docs-me@l2terra.online", RoleName.USER);
        Cookie accessCookie = sessionCookiesFrom(login("docs-me@l2terra.online", "docs-me-login")).accessCookie();

        mockMvc.perform(get("/api/auth/me").cookie(accessCookie))
                .andExpect(status().isOk())
                .andDo(document("auth/me",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        CookieDocumentation.requestCookies(
                                cookieWithName("terra_access_token").description("JWT de acceso del usuario autenticado.")
                        )));
    }

    @Test
    void documentPreferredLanguageUpdate() throws Exception {
        createVerifiedUser("docs-language@l2terra.online", RoleName.USER);
        SessionCookies cookies = sessionCookiesFrom(login("docs-language@l2terra.online", "docs-language-login"));

        mockMvc.perform(patch("/api/auth/preferred-language")
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(cookies.accessCookie(), cookies.refreshCookie(), cookies.csrfCookie())
                        .header("X-CSRF-TOKEN", cookies.csrfToken())
                        .content("""
                                {
                                  "language": "es"
                                }
                                """))
                .andExpect(status().isOk())
                .andDo(document("auth/preferred-language",
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
                        requestFields(
                                fieldWithPath("language").description("Nuevo idioma preferido de la cuenta.")
                        )));
    }

    private org.springframework.test.web.servlet.MvcResult login(String email, String seed) throws Exception {
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
