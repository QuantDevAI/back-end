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
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/** Usuário local do Filmaê. {@code authUserId} é o {@code sub} do JWT do auth-microservice. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "auth_user_id", nullable = false, unique = true)
    private UUID authUserId;

    @Column(name = "email", nullable = false, length = 254)
    private String email;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "bio", nullable = false, columnDefinition = "text")
    @Builder.Default
    private String bio = "";

    @Column(name = "onboarding_completed_at", columnDefinition = "timestamptz")
    private Instant onboardingCompletedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "timestamptz")
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false, columnDefinition = "timestamptz")
    private Instant updatedAt;

    public String getBio() {
        return bio != null ? bio : "";
    }

    public boolean isOnboardingCompleted() {
        return onboardingCompletedAt != null;
    }
}
