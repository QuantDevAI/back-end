package com.filmae.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/** Preferências do perfil. A linha é criada junto com o usuário (ver {@code CurrentUserService}). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "user_preferences")
public class UserPreferences {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "receive_recommendations_by_email", nullable = false)
    @Builder.Default
    private boolean receiveRecommendationsByEmail = true;

    @Column(name = "spoiler_mode_enabled", nullable = false)
    @Builder.Default
    private boolean spoilerModeEnabled = false;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false, columnDefinition = "timestamptz")
    private Instant updatedAt;

    public static UserPreferences defaultsFor(Long userId) {
        return UserPreferences.builder().userId(userId).build();
    }
}
