package com.filmae.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "user_onboarding_favorite_movies")
@IdClass(UserMovieId.class)
public class UserOnboardingFavoriteMovie {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Id
    @Column(name = "tconst", length = 20)
    private String tconst;

    @Column(name = "position", nullable = false)
    private int position;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "timestamptz")
    private Instant createdAt;
}
