package com.terra.api.common.i18n.message;

import com.terra.api.common.i18n.resolver.CurrentLanguageResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MessageResolverTest {

    private final MessageResolver messageResolver = new MessageResolver(new CurrentLanguageResolver());

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void shouldPreferCustomLanguageHeaderOverAcceptLanguage() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Language", "es");
        request.addHeader("Accept-Language", "de-DE,de;q=0.9");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        assertEquals("Inicio de sesion correcto", messageResolver.get("auth.login_success"));
    }

    @Test
    void shouldResolveUtf8MessagesCorrectly() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Language", "es");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        assertEquals(
                "La contraseña debe tener al menos 8 caracteres, 1 mayuscula, 1 numero y 1 caracter especial",
                messageResolver.get("validation.password.strength")
        );
    }

    @Test
    void shouldFallbackToDefaultLanguageWhenHeaderIsUnsupported() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Language", "it");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        assertEquals("Login successful", messageResolver.get("auth.login_success"));
    }
}
