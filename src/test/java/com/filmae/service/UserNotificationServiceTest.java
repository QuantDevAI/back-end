package com.filmae.service;

import com.filmae.dto.notification.NotificationsPayload;
import com.filmae.entity.Notification;
import com.filmae.entity.NotificationType;
import com.filmae.exception.ApiException;
import com.filmae.exception.ErrorCode;
import com.filmae.repository.NotificationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserNotificationService")
class UserNotificationServiceTest {

    private static final Long USER_ID = 3L;

    @Mock private NotificationRepository notificationRepository;

    @InjectMocks private UserNotificationService service;

    @Test
    @DisplayName("list mapeia id público n_<id>, type e unreadCount")
    void listMapsItems() {
        Notification n = notification(31L, false);
        when(notificationRepository.findByUserIdOrderByCreatedAtDescIdDesc(eq(USER_ID), any(Pageable.class)))
            .thenReturn(List.of(n));
        when(notificationRepository.countByUserIdAndReadFalse(USER_ID)).thenReturn(1L);

        NotificationsPayload payload = service.list(USER_ID);

        assertThat(payload.unreadCount()).isEqualTo(1);
        assertThat(payload.items()).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo("n_31");
            assertThat(item.type()).isEqualTo(NotificationType.DAILY_RECOMMENDATION);
            assertThat(item.read()).isFalse();
        });
    }

    @Test
    @DisplayName("PATCH sem body marca como lida e devolve o payload completo")
    void markReadWithoutBodyMarksAsRead() {
        Notification n = notification(31L, false);
        when(notificationRepository.findByIdAndUserId(31L, USER_ID)).thenReturn(Optional.of(n));
        when(notificationRepository.findByUserIdOrderByCreatedAtDescIdDesc(eq(USER_ID), any(Pageable.class)))
            .thenReturn(List.of(n));
        when(notificationRepository.countByUserIdAndReadFalse(USER_ID)).thenReturn(0L);

        NotificationsPayload payload = service.markRead(USER_ID, "n_31", null);

        assertThat(n.isRead()).isTrue();
        assertThat(n.getReadAt()).isNotNull();
        verify(notificationRepository).saveAndFlush(n);
        assertThat(payload.items()).singleElement().satisfies(item -> assertThat(item.read()).isTrue());
        assertThat(payload.unreadCount()).isZero();
    }

    @Test
    @DisplayName("PATCH com read=false volta para não lida")
    void markReadFalseMarksAsUnread() {
        Notification n = notification(31L, true);
        when(notificationRepository.findByIdAndUserId(31L, USER_ID)).thenReturn(Optional.of(n));
        when(notificationRepository.findByUserIdOrderByCreatedAtDescIdDesc(eq(USER_ID), any(Pageable.class)))
            .thenReturn(List.of(n));
        when(notificationRepository.countByUserIdAndReadFalse(USER_ID)).thenReturn(1L);

        service.markRead(USER_ID, "n_31", false);

        assertThat(n.isRead()).isFalse();
        assertThat(n.getReadAt()).isNull();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"31", "n_", "n_abc", "x_31", "n_31a", "N_31", "n_-5", "n_0"})
    @DisplayName("id público inválido responde 404 sem consultar o banco")
    void invalidPublicIdIsNotFound(String publicId) {
        assertThatThrownBy(() -> service.markRead(USER_ID, publicId, true))
            .isInstanceOf(ApiException.class)
            .extracting(ex -> ((ApiException) ex).getCode())
            .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        verify(notificationRepository, never()).findByIdAndUserId(anyLong(), anyLong());
    }

    @Test
    @DisplayName("notificação de outro usuário responde 404")
    void otherUsersNotificationIsNotFound() {
        when(notificationRepository.findByIdAndUserId(99L, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markRead(USER_ID, "n_99", true))
            .isInstanceOf(ApiException.class)
            .extracting(ex -> ((ApiException) ex).getCode())
            .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        verify(notificationRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("read-all marca todas do usuário e devolve o payload recarregado")
    void markAllReadReturnsFullPayload() {
        Notification a = notification(1L, true);
        Notification b = notification(2L, true);
        when(notificationRepository.findByUserIdOrderByCreatedAtDescIdDesc(eq(USER_ID), any(Pageable.class)))
            .thenReturn(List.of(b, a));
        when(notificationRepository.countByUserIdAndReadFalse(USER_ID)).thenReturn(0L);

        NotificationsPayload payload = service.markAllRead(USER_ID);

        verify(notificationRepository).markAllRead(eq(USER_ID), any(Instant.class));
        assertThat(payload.unreadCount()).isZero();
        assertThat(payload.items()).extracting(item -> item.id()).containsExactly("n_2", "n_1");
    }

    private static Notification notification(Long id, boolean read) {
        return Notification.builder().id(id).userId(USER_ID).type(NotificationType.DAILY_RECOMMENDATION)
            .title("Seu filme do dia chegou").message("Interestelar combina com a sessão de hoje.")
            .read(read).readAt(read ? Instant.now() : null).createdAt(Instant.parse("2026-10-02T12:00:00Z")).build();
    }
}
