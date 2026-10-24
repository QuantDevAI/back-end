package com.filmae.service.movie;

import com.filmae.entity.GenreCatalog;
import com.filmae.exception.ApiException;
import com.filmae.exception.ErrorCode;
import com.filmae.repository.GenreCatalogRepository;
import com.filmae.repository.MovieRepository;
import com.filmae.repository.projection.GenreCountView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("MovieCatalogService")
class MovieCatalogServiceTest {

    @Mock private MovieRepository movieRepository;
    @Mock private MovieAssembler movieAssembler;
    @Mock private GenreCatalogRepository genreCatalogRepository;

    private MovieCatalogService service;

    @BeforeEach
    void setUp() {
        when(genreCatalogRepository.findAllByOrderBySortOrderAsc()).thenReturn(List.of(
            new GenreCatalog("acao", "Action", "Ação", true, 10),
            new GenreCatalog("drama", "Drama", "Drama", true, 70),
            new GenreCatalog("ficcao-cientifica", "Sci-Fi", "Ficção Científica", true, 90),
            new GenreCatalog("suspense", "Thriller", "Suspense", false, 120),
            new GenreCatalog("thriller", "Thriller", "Thriller", true, 130),
            new GenreCatalog("faroeste", "Western", "Faroeste", true, 270),
            new GenreCatalog("talk-show", "Talk-Show", "Talk show", true, 320)
        ));
        service = new MovieCatalogService(movieRepository, movieAssembler, new GenreNameResolver(genreCatalogRepository));
        when(movieRepository.search(any(), any(), any(), any(), any(), any(), any(Pageable.class)))
            .thenAnswer(inv -> new PageImpl<>(List.of(), inv.getArgument(6), 0));
        when(movieAssembler.toListItems(any(), anyList())).thenReturn(List.of());
    }

    private static MovieSearchCriteria criteria(List<String> genres, Integer page, Integer size) {
        return new MovieSearchCriteria(null, genres, null, null, null, null, page, size);
    }

    @Test
    @DisplayName("Gêneros aceitam slug do front ou nome IMDb em qualquer caixa e viram CSV de nomes IMDb")
    void normalizesGenres() {
        service.search(1L, criteria(List.of("acao", "sci-fi", "Drama", "suspense", "thriller"), null, null));

        verify(movieRepository).search(isNull(), eq("Action,Sci-Fi,Drama,Thriller"), isNull(), isNull(), isNull(), isNull(),
            eq(PageRequest.of(0, MovieCatalogService.DEFAULT_PAGE_SIZE)));
    }

    @Test
    @DisplayName("size padrão 20, máximo 50; meta da página reflete o pedido")
    void pageSize() {
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);

        var result = service.search(1L, criteria(null, 2, 500));

        verify(movieRepository).search(any(), isNull(), any(), any(), any(), any(), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(50);
        assertThat(pageable.getValue().getSort().isUnsorted()).isTrue();
        assertThat(result.getMeta().getPage()).isEqualTo(2);
        assertThat(result.getMeta().getSize()).isEqualTo(50);
    }

    @Test
    @DisplayName("page < 0, size < 1, yearFrom > yearTo e minRating fora de 0..10 → VALIDATION_ERROR")
    void validation() {
        assertValidation(criteria(null, -1, null), "page");
        assertValidation(criteria(null, 0, 0), "size");
        assertValidation(new MovieSearchCriteria(null, null, null, 2010, 2000, null, null, null), "yearFrom");
        assertValidation(new MovieSearchCriteria(null, null, null, null, null, new BigDecimal("11"), null, null), "minRating");
        verifyNoInteractions(movieAssembler);
    }

    @Test
    @DisplayName("type movie/series vira title_type do IMDb e q é escapado para ILIKE")
    void typeAndQuery() {
        assertThat(MovieCatalogService.titleTypeFor("series")).isEqualTo("tvSeries");
        assertThat(MovieCatalogService.titleTypeFor("movie")).isEqualTo("movie");
        assertThat(MovieCatalogService.titleTypeFor("tvMiniSeries")).isEqualTo("tvMiniSeries");
        assertThat(MovieCatalogService.titleTypeFor(" ")).isNull();
        assertThat(MovieCatalogService.escapeLike(" 100%_a\\b ")).isEqualTo("100\\%\\_a\\\\b");
        assertThat(MovieCatalogService.escapeLike("")).isNull();
    }

    @Test
    @DisplayName("GET /genres: canônicos com nome em português; extras só com filmes; IMDb fora do catálogo no fim")
    void listGenres() {
        when(movieRepository.countMoviesByGenre()).thenReturn(List.of(
            count("Drama", 15), count("Western", 2), count("Sci-Fi", 4), count("Animation", 1)));

        assertThat(service.listGenres()).extracting("id", "name", "movieCount").containsExactly(
            tuple("Action", "Ação", 0L),
            tuple("Drama", "Drama", 15L),
            tuple("Sci-Fi", "Ficção Científica", 4L),
            tuple("Thriller", "Thriller", 0L),
            tuple("Western", "Faroeste", 2L),
            tuple("Animation", "Animation", 1L));
    }

    @Test
    @DisplayName("Filme inexistente → 404 RESOURCE_NOT_FOUND")
    void notFound() {
        when(movieRepository.findById("tt404")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.details(1L, "tt404"))
            .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private void assertValidation(MovieSearchCriteria criteria, String field) {
        assertThatThrownBy(() -> service.search(1L, criteria))
            .isInstanceOfSatisfying(ApiException.class, ex -> {
                assertThat(ex.getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                assertThat(ex.getFields()).extracting("field").containsExactly(field);
            });
    }

    private static GenreCountView count(String genre, long movieCount) {
        return new GenreCountView() {
            @Override public String getGenre() { return genre; }
            @Override public Long getMovieCount() { return movieCount; }
        };
    }
}
