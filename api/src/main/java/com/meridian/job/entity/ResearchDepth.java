package com.meridian.job.entity;

import java.util.Arrays;
import java.util.Optional;

/**
 * Enum for research depth configuration
 */
public enum ResearchDepth {
    QUICK("QUICK"),
    STANDARD("STANDARD"),
    DEEP("DEEP");

    private final String value;

    ResearchDepth(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static Optional<ResearchDepth> fromValue(String value) {
        return Arrays.stream(values())
            .filter(depth -> depth.value.equals(value))
            .findFirst();
    }
}
