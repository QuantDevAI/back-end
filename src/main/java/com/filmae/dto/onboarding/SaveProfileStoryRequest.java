package com.filmae.dto.onboarding;

/** Texto livre; string vazia é aceita (o front envia {@code ""} quando o usuário pula a etapa). */
public record SaveProfileStoryRequest(String text) {
}
