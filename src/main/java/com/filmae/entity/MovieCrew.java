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

/** Diretores e roteiristas (nconsts) de um título (tabela {@code equipe_filme}). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "equipe_filme")
public class MovieCrew {

    @Id
    @Column(name = "tconst", length = 20)
    private String tconst;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "directors", columnDefinition = "text[]")
    @Builder.Default
    private List<String> directors = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "writers", columnDefinition = "text[]")
    @Builder.Default
    private List<String> writers = new ArrayList<>();

    public List<String> getDirectors() {
        return directors != null ? directors : List.of();
    }

    public List<String> getWriters() {
        return writers != null ? writers : List.of();
    }
}
