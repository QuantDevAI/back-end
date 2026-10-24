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
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Progresso do onboarding. {@code genres} guarda os nomes do IMDb (normalizados via {@link GenreCatalog}).
 * Os filmes favoritos ficam em {@link UserOnboardingFavoriteMovie}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "user_onboarding")
public class UserOnboarding {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "genres", nullable = false, columnDefinition = "text[]")
    @Builder.Default
    private List<String> genres = new ArrayList<>();

    @Column(name = "profile_story", columnDefinition = "text")
    private String profileStory;

    @Column(name = "current_step", nullable = false, length = 20)
    @Builder.Default
    private OnboardingStep currentStep = OnboardingStep.GENRES;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "timestamptz")
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false, columnDefinition = "timestamptz")
    private Instant updatedAt;

    public List<String> getGenres() {
        return genres != null ? genres : List.of();
    }

    public static UserOnboarding emptyFor(Long userId) {
        return UserOnboarding.builder().userId(userId).build();
    }
}
