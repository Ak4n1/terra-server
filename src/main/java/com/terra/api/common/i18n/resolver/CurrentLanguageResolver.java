package com.terra.api.common.i18n.resolver;

import com.terra.api.common.i18n.model.SupportedLanguage;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
public class CurrentLanguageResolver {

    public SupportedLanguage resolve() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return SupportedLanguage.defaultLanguage();
        }

        HttpServletRequest request = attributes.getRequest();
        SupportedLanguage byCustomHeader = SupportedLanguage.fromValue(request.getHeader("X-Language"));
        if (request.getHeader("X-Language") != null && !request.getHeader("X-Language").isBlank()) {
            return byCustomHeader;
        }

        String acceptLanguage = request.getHeader("Accept-Language");
        if (acceptLanguage != null && !acceptLanguage.isBlank()) {
            return SupportedLanguage.fromValue(acceptLanguage);
        }

        return SupportedLanguage.defaultLanguage();
    }
}
