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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;

/** Título do IMDb (tabela {@code filmes} do ETL). {@code genres} guarda os nomes do IMDb (ex.: {@code Sci-Fi}). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "filmes")
public class Movie {

    @Id
    @Column(name = "tconst", length = 20)
    private String tconst;

    @Column(name = "title_type", nullable = false, length = 30)
    private String titleType;

    @Column(name = "primary_title", nullable = false, columnDefinition = "text")
    private String primaryTitle;

    @Column(name = "original_title", nullable = false, columnDefinition = "text")
    private String originalTitle;

    @Column(name = "is_adult", nullable = false)
    private boolean adult;

    @Column(name = "start_year")
    private Integer startYear;

    @Column(name = "end_year")
    private Integer endYear;

    @Column(name = "runtime_minutes")
    private Integer runtimeMinutes;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "genres", columnDefinition = "text[]")
    @Builder.Default
    private List<String> genres = new ArrayList<>();

    public List<String> getGenres() {
        return genres != null ? genres : List.of();
    }
}
