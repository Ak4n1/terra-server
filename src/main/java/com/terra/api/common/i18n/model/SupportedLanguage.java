package com.terra.api.common.i18n.model;

import java.util.Arrays;
import java.util.Optional;

public enum SupportedLanguage {
    US("us"),
    ES("es"),
    PT("pt"),
    FR("fr"),
    DE("de");

    private final String code;

    SupportedLanguage(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static SupportedLanguage defaultLanguage() {
        return US;
    }

    public static SupportedLanguage fromValue(String value) {
        if (value == null || value.isBlank()) {
            return defaultLanguage();
        }

        String normalizedValue = value.trim().toLowerCase();
        if (normalizedValue.contains(",")) {
            normalizedValue = normalizedValue.substring(0, normalizedValue.indexOf(','));
        }
        if (normalizedValue.contains("-")) {
            normalizedValue = normalizedValue.substring(0, normalizedValue.indexOf('-'));
        }
        if (normalizedValue.contains("_")) {
            normalizedValue = normalizedValue.substring(0, normalizedValue.indexOf('_'));
        }

        String candidate = normalizedValue;
        return Arrays.stream(values())
                .filter(language -> language.code.equals(candidate))
                .findFirst()
                .orElse(defaultLanguage());
    }

    public static Optional<SupportedLanguage> findByCode(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }

        String normalizedValue = value.trim().toLowerCase();
        return Arrays.stream(values())
                .filter(language -> language.code.equals(normalizedValue))
                .findFirst();
    }
}
