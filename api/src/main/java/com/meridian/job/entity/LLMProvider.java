package com.meridian.job.entity;

import java.util.Arrays;
import java.util.Optional;

/**
 * Enum for LLM provider selection
 */
public enum LLMProvider {
    BEDROCK("BEDROCK"),
    OPENAI("OPENAI");

    private final String value;

    LLMProvider(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static Optional<LLMProvider> fromValue(String value) {
        return Arrays.stream(values())
            .filter(provider -> provider.value.equals(value))
            .findFirst();
    }
}
