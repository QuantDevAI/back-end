package com.filmae.dto.notification;

/** Body opcional do PATCH {@code /users/me/notifications/{id}}; sem body ou sem {@code read}, marca como lida. */
public record UpdateNotificationRequest(Boolean read) {
}
