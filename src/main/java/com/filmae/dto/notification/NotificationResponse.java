package com.filmae.dto.notification;

import com.filmae.entity.Notification;
import com.filmae.entity.NotificationType;

import java.time.Instant;

/** {@code AppNotification} do front; {@code id} no formato {@code n_<id>}. */
public record NotificationResponse(
    String id,
    String title,
    String message,
    Instant createdAt,
    boolean read,
    NotificationType type
) {

    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
            notification.getPublicId(),
            notification.getTitle(),
            notification.getMessage(),
            notification.getCreatedAt(),
            notification.isRead(),
            notification.getType()
        );
    }
}
