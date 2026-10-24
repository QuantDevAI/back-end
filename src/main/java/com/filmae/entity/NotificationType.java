package com.filmae.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

/** {@code AppNotificationType} do front; {@link #getValue()} é o texto usado no banco e na API. */
public enum NotificationType {

    COLLECTION("collection"),
    DAILY_RECOMMENDATION("daily_recommendation"),
    ONBOARDING("onboarding"),
    SOCIAL("social"),
    SYSTEM("system");

    private final String value;

    NotificationType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static NotificationType fromValue(String value) {
        return Arrays.stream(values())
            .filter(type -> type.value.equals(value))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Tipo de notificação desconhecido: " + value));
    }
}
