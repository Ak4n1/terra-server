package com.terra.api.common.i18n.message;

import com.terra.api.common.i18n.model.SupportedLanguage;
import com.terra.api.common.i18n.resolver.CurrentLanguageResolver;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.MessageFormat;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class MessageResolver {

    private static final String COMMON_MODULE = "common";

    private final CurrentLanguageResolver currentLanguageResolver;
    private final Map<String, Properties> cache = new ConcurrentHashMap<>();

    public MessageResolver(CurrentLanguageResolver currentLanguageResolver) {
        this.currentLanguageResolver = currentLanguageResolver;
    }

    public String get(String key, Object... args) {
        SupportedLanguage language = currentLanguageResolver.resolve();
        String message = resolveMessage(resolveModule(key), key, language);
        if (args.length == 0) {
            return message;
        }
        return MessageFormat.format(message, args);
    }

    public String getFromModule(String module, String key, SupportedLanguage language, Object... args) {
        String message = resolveMessage(module, key, language);
        if (args.length == 0) {
            return message;
        }
        return MessageFormat.format(message, args);
    }

    private String resolveMessage(String module, String key, SupportedLanguage language) {
        String localizedMessage = loadProperties(module, language).getProperty(key);
        if (localizedMessage != null) {
            return localizedMessage;
        }

        if (language != SupportedLanguage.defaultLanguage()) {
            String fallbackMessage = loadProperties(module, SupportedLanguage.defaultLanguage()).getProperty(key);
            if (fallbackMessage != null) {
                return fallbackMessage;
            }
        }

        String commonMessage = loadProperties(COMMON_MODULE, language).getProperty(key);
        if (commonMessage != null) {
            return commonMessage;
        }

        if (language != SupportedLanguage.defaultLanguage()) {
            String commonFallback = loadProperties(COMMON_MODULE, SupportedLanguage.defaultLanguage()).getProperty(key);
            if (commonFallback != null) {
                return commonFallback;
            }
        }

        return key;
    }

    private String resolveModule(String key) {
        int separatorIndex = key.indexOf('.');
        if (separatorIndex <= 0) {
            return COMMON_MODULE;
        }

        String prefix = key.substring(0, separatorIndex);
        return switch (prefix) {
            case "auth", "validation", "common", "security" -> prefix;
            default -> COMMON_MODULE;
        };
    }

    private Properties loadProperties(String module, SupportedLanguage language) {
        String cacheKey = module + ":" + language.getCode();
        return cache.computeIfAbsent(cacheKey, ignored -> {
            Properties properties = new Properties();
            ClassPathResource resource = new ClassPathResource("i18n/" + module + "/messages." + language.getCode() + ".properties");
            if (!resource.exists()) {
                return properties;
            }

            try (InputStream inputStream = resource.getInputStream();
                 InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
                properties.load(reader);
                return properties;
            } catch (IOException exception) {
                throw new IllegalStateException("Failed to load messages for module " + module + " and language " + language.getCode(), exception);
            }
        });
    }
}
