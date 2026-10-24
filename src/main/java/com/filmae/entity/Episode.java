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

/** Episódio de série (tabela {@code episodios}). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "episodios")
public class Episode {

    @Id
    @Column(name = "tconst", length = 20)
    private String tconst;

    @Column(name = "parent_tconst", nullable = false, length = 20)
    private String parentTconst;

    @Column(name = "season_number")
    private Integer seasonNumber;

    @Column(name = "episode_number")
    private Integer episodeNumber;
}
