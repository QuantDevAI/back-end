package com.filmae.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Notification")
class NotificationTest {

    @Test
    @DisplayName("parsePublicId converte n_<id> no id numérico")
    void parsesValidId() {
        assertThat(Notification.parsePublicId("n_31")).hasValue(31L);
        assertThat(Notification.parsePublicId("n_9007199254740993")).hasValue(9007199254740993L);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"31", "n_", "n_abc", "N_31", "x_31", "n_31x", "n_99999999999999999999", "n_ 31", "n_-5", "n_0"})
    @DisplayName("parsePublicId devolve vazio para formatos inválidos")
    void rejectsInvalidIds(String input) {
        assertThat(Notification.parsePublicId(input)).isEmpty();
    }

    @Test
    @DisplayName("getPublicId usa o prefixo n_ e é nulo antes de persistir")
    void publicId() {
        assertThat(Notification.builder().id(31L).build().getPublicId()).isEqualTo("n_31");
        assertThat(new Notification().getPublicId()).isNull();
    }

    @Test
    @DisplayName("markRead define e limpa readAt")
    void markRead() {
        Notification notification = new Notification();
        notification.markRead(true);
        assertThat(notification.isRead()).isTrue();
        assertThat(notification.getReadAt()).isNotNull();

        notification.markRead(false);
        assertThat(notification.isRead()).isFalse();
        assertThat(notification.getReadAt()).isNull();
    }
}
