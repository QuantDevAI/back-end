package com.filmae.service;

import com.filmae.dto.common.ApiFieldError;
import com.filmae.entity.GenreCatalog;
import com.filmae.exception.ApiException;
import com.filmae.exception.ErrorCode;
import com.filmae.repository.GenreCatalogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("GenreResolver")
class GenreResolverTest {

    @Mock private GenreCatalogRepository repository;
    @InjectMocks private GenreResolver resolver;

    private static GenreCatalog genre(String slug, String imdb, boolean canonical, int order) {
        return GenreCatalog.builder().slug(slug).imdbName(imdb).namePt(imdb).canonical(canonical).sortOrder(order).build();
    }

    @BeforeEach
    void catalog() {
        when(repository.findAllByOrderBySortOrderAsc()).thenReturn(List.of(
            genre("acao", "Action", true, 10),
            genre("drama", "Drama", true, 70),
            genre("ficcao-cientifica", "Sci-Fi", true, 90),
            genre("terror", "Horror", true, 100),
            genre("suspense", "Thriller", false, 120),
            genre("thriller", "Thriller", true, 130),
            genre("noir", "Film-Noir", true, 280)
        ));
    }

    @Test
    @DisplayName("Traduz slugs do front para nomes do IMDb")
    void slugsToImdb() {
        assertThat(resolver.resolveAll(List.of("ficcao-cientifica", "terror", "acao"), "genres"))
            .containsExactly("Sci-Fi", "Horror", "Action");
    }

    @Test
    @DisplayName("Aceita nomes do IMDb sem diferenciar maiúsculas e com espaços nas bordas")
    void imdbNamesCaseInsensitive() {
        assertThat(resolver.resolveAll(List.of("Sci-Fi", " drama ", "FILM-NOIR"), "genres"))
            .containsExactly("Sci-Fi", "Drama", "Film-Noir");
    }

    @Test
    @DisplayName("Alias e duplicatas viram um único gênero, na ordem recebida")
    void aliasesAndDuplicates() {
        assertThat(resolver.resolveAll(List.of("suspense", "drama", "thriller", "Thriller", "Drama"), "genres"))
            .containsExactly("Thriller", "Drama");
    }

    @Test
    @DisplayName("Gênero desconhecido, vazio ou nulo gera VALIDATION_ERROR com índice no field")
    void invalidGenres() {
        assertThatThrownBy(() -> resolver.resolveAll(Arrays.asList("drama", "novela", " ", null), "genres"))
            .isInstanceOfSatisfying(ApiException.class, ex -> {
                assertThat(ex.getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                assertThat(ex.getFields()).extracting(ApiFieldError::getField)
                    .containsExactly("genres[1]", "genres[2]", "genres[3]");
                assertThat(ex.getFields().get(0).getMessage()).contains("novela");
            });
    }
}
