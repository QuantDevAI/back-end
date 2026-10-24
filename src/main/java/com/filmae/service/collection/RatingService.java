package com.filmae.service.collection;

import com.filmae.dto.collection.CollectionMovieResponse;
import com.filmae.dto.rating.MovieRatingValueResponse;
import com.filmae.entity.Movie;
import com.filmae.entity.UserMovieId;
import com.filmae.entity.UserMovieRating;
import com.filmae.exception.ApiException;
import com.filmae.repository.UserMovieRatingRepository;
import com.filmae.service.movie.MovieAssembler;
import com.filmae.service.movie.MovieCatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

/** Nota do usuário (0.5 a 5, passo 0.5), independente da nota do IMDb e dos estados da coleção. */
@Service
@Transactional
@RequiredArgsConstructor
public class RatingService {

    static final BigDecimal MIN_RATING = new BigDecimal("0.5");
    static final BigDecimal MAX_RATING = new BigDecimal("5.0");
    static final String INVALID_RATING_MESSAGE = "A nota deve estar entre 0.5 e 5, em passos de 0.5";

    private final UserMovieRatingRepository userMovieRatingRepository;
    private final MovieCatalogService movieCatalogService;
    private final MovieAssembler movieAssembler;
    private final CollectionSummaryService collectionSummaryService;

    public CollectionMovieResponse save(Long userId, String tconst, BigDecimal rating) {
        BigDecimal value = validate(rating);
        Movie movie = movieCatalogService.requireMovie(tconst);
        UserMovieRating entity = userMovieRatingRepository.findById(UserMovieId.of(userId, tconst))
            .orElseGet(() -> UserMovieRating.builder().userId(userId).tconst(tconst).build());
        Instant ratedAt = CollectionService.now();
        entity.setRating(value);
        entity.setRatedAt(ratedAt);
        userMovieRatingRepository.saveAndFlush(entity);
        return response(userId, movie, ratedAt);
    }

    @Transactional(readOnly = true)
    public MovieRatingValueResponse get(Long userId, String tconst) {
        movieCatalogService.requireMovie(tconst);
        return new MovieRatingValueResponse(userMovieRatingRepository.findById(UserMovieId.of(userId, tconst))
            .map(rating -> rating.getRating().doubleValue())
            .orElse(null));
    }

    public CollectionMovieResponse delete(Long userId, String tconst) {
        Movie movie = movieCatalogService.requireMovie(tconst);
        userMovieRatingRepository.findById(UserMovieId.of(userId, tconst)).ifPresent(rating -> {
            userMovieRatingRepository.delete(rating);
            userMovieRatingRepository.flush();
        });
        return response(userId, movie, CollectionService.now());
    }

    /** Aceita 0.5 a 5 em passos de 0.5 (ex.: 3, 3.5, "4.50"); devolve com escala 1. Senão, 400 VALIDATION_ERROR. */
    static BigDecimal validate(BigDecimal rating) {
        if (rating == null
            || rating.compareTo(MIN_RATING) < 0
            || rating.compareTo(MAX_RATING) > 0
            || rating.multiply(BigDecimal.valueOf(2)).stripTrailingZeros().scale() > 0) {
            throw ApiException.validation("rating", INVALID_RATING_MESSAGE);
        }
        return rating.setScale(1, RoundingMode.UNNECESSARY);
    }

    private CollectionMovieResponse response(Long userId, Movie movie, Instant updatedAt) {
        return CollectionMovieResponse.builder()
            .movie(movieAssembler.toListItem(userId, movie))
            .summary(collectionSummaryService.summary(userId))
            .updatedAt(updatedAt)
            .build();
    }
}
