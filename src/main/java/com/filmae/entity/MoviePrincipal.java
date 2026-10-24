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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;
import java.util.Set;

/**
 * Elenco/equipe principal de um título (tabela {@code principais_filme}), ordenado por {@code ordering}.
 * {@code category} segue o IMDb: {@code actor}, {@code actress}, {@code self}, {@code director}, {@code writer}...
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "principais_filme")
@IdClass(MoviePrincipalId.class)
public class MoviePrincipal {

    public static final Set<String> CAST_CATEGORIES = Set.of("actor", "actress", "self");

    @Id
    @Column(name = "tconst", length = 20)
    private String tconst;

    @Id
    @Column(name = "ordering")
    private Integer ordering;

    @Column(name = "nconst", nullable = false, length = 20)
    private String nconst;

    @Column(name = "category", nullable = false, length = 50)
    private String category;

    @Column(name = "job")
    private String job;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "characters", columnDefinition = "jsonb")
    private List<String> characters;

    public boolean isCast() {
        return category != null && CAST_CATEGORIES.contains(category);
    }
}
