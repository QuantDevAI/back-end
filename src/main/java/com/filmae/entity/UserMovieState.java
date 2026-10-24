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
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * Estado do filme na coleção do usuário: quatro flags independentes. Um filme está "na coleção"
 * quando qualquer flag é verdadeira ({@link #isInCollection()}).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "user_movie_states")
@IdClass(UserMovieId.class)
public class UserMovieState {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Id
    @Column(name = "tconst", length = 20)
    private String tconst;

    @Column(name = "watchlist", nullable = false)
    private boolean watchlist;

    @Column(name = "watched", nullable = false)
    private boolean watched;

    @Column(name = "favorite", nullable = false)
    private boolean favorite;

    @Column(name = "rewatch", nullable = false)
    private boolean rewatch;

    @Column(name = "watched_at", columnDefinition = "timestamptz")
    private Instant watchedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "timestamptz")
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false, columnDefinition = "timestamptz")
    private Instant updatedAt;

    public static UserMovieState emptyFor(Long userId, String tconst) {
        return UserMovieState.builder().userId(userId).tconst(tconst).build();
    }

    public boolean isInCollection() {
        return watchlist || watched || favorite || rewatch;
    }

    public void clearFlags() {
        watchlist = false;
        watched = false;
        favorite = false;
        rewatch = false;
    }
}
