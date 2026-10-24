package com.filmae.dto.notification;

import java.util.List;

/** {@code NotificationsPayload} do front: resposta do GET, do PATCH {@code /{id}} e do {@code read-all}. */
public record NotificationsPayload(List<NotificationResponse> items, long unreadCount) {
}
