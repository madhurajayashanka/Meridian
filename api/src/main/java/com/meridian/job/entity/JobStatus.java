package com.meridian.job.entity;

import java.util.Arrays;
import java.util.Optional;

/**
 * Enum for research job status states
 */
public enum JobStatus {
    PENDING("PENDING"),
    RUNNING("RUNNING"),
    COMPLETE("COMPLETE"),
    FAILED("FAILED"),
    CANCELLED("CANCELLED");

    private final String value;

    JobStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static Optional<JobStatus> fromValue(String value) {
        return Arrays.stream(values())
            .filter(status -> status.value.equals(value))
            .findFirst();
    }
}
