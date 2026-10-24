package com.filmae.service;

import com.filmae.dto.notification.NotificationResponse;
import com.filmae.dto.notification.NotificationsPayload;
import com.filmae.entity.Notification;
import com.filmae.exception.ApiException;
import com.filmae.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/** Leitura e marcação de leitura das notificações do usuário corrente. */
@Service
@Transactional
@RequiredArgsConstructor
public class UserNotificationService {

    static final int LIST_LIMIT = 50;

    private final NotificationRepository notificationRepository;

    @Transactional(readOnly = true)
    public NotificationsPayload list(Long userId) {
        List<NotificationResponse> items = notificationRepository
            .findByUserIdOrderByCreatedAtDescIdDesc(userId, PageRequest.of(0, LIST_LIMIT)).stream()
            .map(NotificationResponse::from)
            .toList();
        return new NotificationsPayload(items, notificationRepository.countByUserIdAndReadFalse(userId));
    }

    /** {@code read == null} marca como lida. Id mal formatado ou de outro usuário vira 404. */
    public NotificationsPayload markRead(Long userId, String publicId, Boolean read) {
        Notification notification = Notification.parsePublicId(publicId)
            .flatMap(id -> notificationRepository.findByIdAndUserId(id, userId))
            .orElseThrow(() -> ApiException.notFound("Notificação não encontrada"));
        notification.markRead(read == null || read);
        notificationRepository.saveAndFlush(notification);
        return list(userId);
    }

    public NotificationsPayload markAllRead(Long userId) {
        notificationRepository.markAllRead(userId, Instant.now());
        return list(userId);
    }
}
