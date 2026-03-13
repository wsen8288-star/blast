package com.blastfurnace.backend.model;

import com.fasterxml.jackson.annotation.JsonValue;

public enum DeploymentStatus {
    RUNNING("running"),
    COMPLETED("completed"),
    FAILED("failed"),
    CANCELED("canceled");

    private final String value;

    DeploymentStatus(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}
