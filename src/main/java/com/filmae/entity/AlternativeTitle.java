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

import java.util.ArrayList;
import java.util.List;

/** Título alternativo do IMDb (tabela {@code titulos_alternativos}); região {@code BR} dá o título em português. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "titulos_alternativos")
@IdClass(AlternativeTitleId.class)
public class AlternativeTitle {

    public static final String REGION_BR = "BR";

    @Id
    @Column(name = "title_id", length = 20)
    private String titleId;

    @Id
    @Column(name = "ordering")
    private Integer ordering;

    @Column(name = "title", nullable = false, columnDefinition = "text")
    private String title;

    @Column(name = "region", length = 10)
    private String region;

    @Column(name = "language", length = 10)
    private String language;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "types", columnDefinition = "text[]")
    @Builder.Default
    private List<String> types = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "attributes", columnDefinition = "text[]")
    @Builder.Default
    private List<String> attributes = new ArrayList<>();

    @Column(name = "is_original_title", nullable = false)
    private boolean originalTitle;
}
