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

/** Pessoa do IMDb (tabela {@code pessoas}). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "pessoas")
public class Person {

    @Id
    @Column(name = "nconst", length = 20)
    private String nconst;

    @Column(name = "primary_name", nullable = false)
    private String primaryName;

    @Column(name = "birth_year")
    private Integer birthYear;

    @Column(name = "death_year")
    private Integer deathYear;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "primary_profession", columnDefinition = "text[]")
    @Builder.Default
    private List<String> primaryProfession = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "known_for_titles", columnDefinition = "text[]")
    @Builder.Default
    private List<String> knownForTitles = new ArrayList<>();
}
