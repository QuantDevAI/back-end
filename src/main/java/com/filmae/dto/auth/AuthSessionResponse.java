package com.filmae.dto.auth;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * {@code AuthSession} (register, sem {@code nextScreen}) e {@code LoginResponse} (login, com {@code nextScreen}).
 * {@code expiresIn} em segundos.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuthSessionResponse(
    AuthUserDto user,
    String accessToken,
    String refreshToken,
    long expiresIn,
    NextScreen nextScreen
) {

    public enum NextScreen {
        HOME("home"),
        ONBOARDING("onboarding");

        private final String value;

        NextScreen(String value) {
            this.value = value;
        }

        @JsonValue
        public String getValue() {
            return value;
        }

        public static NextScreen forOnboardingCompleted(boolean onboardingCompleted) {
            return onboardingCompleted ? HOME : ONBOARDING;
        }
    }

    @Override
    public String toString() {
        return "AuthSessionResponse[user=" + user + ", expiresIn=" + expiresIn + ", nextScreen=" + nextScreen + "]";
    }
}
