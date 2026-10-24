package com.filmae.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.Optional;

/** Notificação do usuário. O id público na API é {@code n_<id>} ({@link #getPublicId()} / {@link #parsePublicId(String)}). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "notifications")
public class Notification {

    public static final String PUBLIC_ID_PREFIX = "n_";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "type", nullable = false, length = 30)
    private NotificationType type;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "message", nullable = false, columnDefinition = "text")
    private String message;

    @Column(name = "read", nullable = false)
    private boolean read;

    /** Evita duplicatas por usuário (UNIQUE user_id + dedupe_key), ex.: {@code daily_recommendation:2026-10-24}. */
    @Column(name = "dedupe_key", length = 120)
    private String dedupeKey;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "timestamptz")
    private Instant createdAt;

    @Column(name = "read_at", columnDefinition = "timestamptz")
    private Instant readAt;

    public String getPublicId() {
        return id != null ? PUBLIC_ID_PREFIX + id : null;
    }

    public void markRead(boolean value) {
        this.read = value;
        this.readAt = value ? Instant.now() : null;
    }

    /** Converte {@code n_31} em {@code 31}; vazio se o formato for inválido. */
    public static Optional<Long> parsePublicId(String publicId) {
        if (publicId == null || !publicId.startsWith(PUBLIC_ID_PREFIX)) {
            return Optional.empty();
        }
        try {
            long id = Long.parseLong(publicId.substring(PUBLIC_ID_PREFIX.length()));
            return id > 0 ? Optional.of(id) : Optional.empty();
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }
}
