package com.filmae.controller;

import com.filmae.dto.common.ApiResponse;
import com.filmae.dto.notification.NotificationsPayload;
import com.filmae.dto.notification.UpdateNotificationRequest;
import com.filmae.security.CurrentUser;
import com.filmae.service.UserNotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me/notifications")
@Tag(name = "Notificações")
@RequiredArgsConstructor
public class NotificationController {

    private final UserNotificationService notificationService;

    @Operation(summary = "Notificações do usuário e quantidade de não lidas")
    @GetMapping
    public ApiResponse<NotificationsPayload> list(@CurrentUser Long userId) {
        return ApiResponse.of(notificationService.list(userId));
    }

    @Operation(summary = "Marca uma notificação como lida (ou não lida com {\"read\": false}); devolve a lista completa")
    @PatchMapping("/{id}")
    public ApiResponse<NotificationsPayload> update(@CurrentUser Long userId,
                                                    @PathVariable("id") String id,
                                                    @RequestBody(required = false) UpdateNotificationRequest request) {
        return ApiResponse.of(notificationService.markRead(userId, id, request != null ? request.read() : null));
    }

    @Operation(summary = "Marca todas como lidas; devolve a lista completa")
    @PostMapping("/read-all")
    public ApiResponse<NotificationsPayload> readAll(@CurrentUser Long userId) {
        return ApiResponse.of(notificationService.markAllRead(userId));
    }
}
