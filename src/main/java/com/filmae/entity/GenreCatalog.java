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

/**
 * Ponte slug do front ({@code ficcao-cientifica}) ↔ gênero IMDb ({@code Sci-Fi}) ↔ nome em português.
 * Aliases (ex.: {@code suspense} → {@code Thriller}) têm {@code canonical = false}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "genre_catalog")
public class GenreCatalog {

    @Id
    @Column(name = "slug", length = 40)
    private String slug;

    @Column(name = "imdb_name", nullable = false, length = 40)
    private String imdbName;

    @Column(name = "name_pt", nullable = false, length = 60)
    private String namePt;

    @Column(name = "canonical", nullable = false)
    private boolean canonical;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
