package com.filmae.service.movie;

import com.filmae.dto.common.PagedData;
import com.filmae.dto.movie.GenreResponse;
import com.filmae.dto.movie.MovieDetailsResponse;
import com.filmae.dto.movie.MovieListItemResponse;
import com.filmae.entity.GenreCatalog;
import com.filmae.entity.Movie;
import com.filmae.exception.ApiException;
import com.filmae.repository.MovieRepository;
import com.filmae.repository.projection.GenreCountView;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class MovieCatalogService {

    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 50;
    /** Gêneros com {@code sort_order} abaixo disso são os do onboarding e sempre aparecem em GET /genres. */
    static final int ONBOARDING_SORT_ORDER_LIMIT = 200;

    private final MovieRepository movieRepository;
    private final MovieAssembler movieAssembler;
    private final GenreNameResolver genreNameResolver;

    /**
     * Gêneros canônicos do catálogo na ordem do {@code genre_catalog}. Os do onboarding aparecem mesmo
     * sem filmes; os demais só se houver filmes. Gêneros do IMDb fora do catálogo vão no fim.
     */
    public List<GenreResponse> listGenres() {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (GenreCountView view : movieRepository.countMoviesByGenre()) {
            counts.put(view.getGenre(), view.getMovieCount() != null ? view.getMovieCount() : 0L);
        }

        List<GenreResponse> genres = new ArrayList<>();
        for (GenreCatalog entry : genreNameResolver.canonicalGenres()) {
            long count = counts.getOrDefault(entry.getImdbName(), 0L);
            if (count > 0 || entry.getSortOrder() < ONBOARDING_SORT_ORDER_LIMIT) {
                genres.add(new GenreResponse(entry.getImdbName(), entry.getNamePt(), count));
            }
            counts.remove(entry.getImdbName());
        }
        counts.forEach((genre, count) -> genres.add(new GenreResponse(genre, genre, count)));
        return genres;
    }

    public PagedData<MovieListItemResponse> search(Long userId, MovieSearchCriteria criteria) {
        int page = criteria.page() != null ? criteria.page() : 0;
        int size = criteria.size() != null ? criteria.size() : DEFAULT_PAGE_SIZE;
        if (page < 0) {
            throw ApiException.validation("page", "A página começa em 0");
        }
        if (size < 1) {
            throw ApiException.validation("size", "O tamanho da página deve ser pelo menos 1");
        }
        size = Math.min(size, MAX_PAGE_SIZE);
        if (criteria.yearFrom() != null && criteria.yearTo() != null && criteria.yearFrom() > criteria.yearTo()) {
            throw ApiException.validation("yearFrom", "yearFrom não pode ser maior que yearTo");
        }
        BigDecimal minRating = criteria.minRating();
        if (minRating != null && (minRating.signum() < 0 || minRating.compareTo(BigDecimal.TEN) > 0)) {
            throw ApiException.validation("minRating", "A nota mínima deve estar entre 0 e 10");
        }

        List<String> genres = genreNameResolver.toImdbNames(criteria.genres() != null ? criteria.genres() : List.of());
        Page<Movie> result = movieRepository.search(
            escapeLike(criteria.q()),
            genres.isEmpty() ? null : String.join(",", genres),
            titleTypeFor(criteria.type()),
            criteria.yearFrom(),
            criteria.yearTo(),
            minRating,
            PageRequest.of(page, size)
        );
        return PagedData.of(movieAssembler.toListItems(userId, result.getContent()), result);
    }

    public MovieDetailsResponse details(Long userId, String tconst) {
        return movieAssembler.toDetails(userId, requireMovie(tconst));
    }

    public Movie requireMovie(String tconst) {
        return movieRepository.findById(tconst)
            .orElseThrow(() -> ApiException.notFound("Filme não encontrado"));
    }

    /** {@code movie} → {@code movie}, {@code series} → {@code tvSeries}; outros valores são tipos IMDb crus. */
    static String titleTypeFor(String type) {
        if (!StringUtils.hasText(type)) {
            return null;
        }
        String value = type.trim();
        return MovieAssembler.TYPE_SERIES.equalsIgnoreCase(value) ? "tvSeries"
            : MovieAssembler.TYPE_MOVIE.equalsIgnoreCase(value) ? "movie"
            : value;
    }

    /** Escapa {@code \}, {@code %} e {@code _} para o ILIKE; vazio vira {@code null} (sem filtro). */
    static String escapeLike(String q) {
        if (!StringUtils.hasText(q)) {
            return null;
        }
        return q.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
