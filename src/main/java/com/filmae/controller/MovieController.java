package com.filmae.controller;

import com.filmae.dto.common.ApiResponse;
import com.filmae.dto.common.PagedData;
import com.filmae.dto.movie.GenreResponse;
import com.filmae.dto.movie.MovieDetailsResponse;
import com.filmae.dto.movie.MovieListItemResponse;
import com.filmae.security.CurrentUser;
import com.filmae.service.movie.GenreFilterParser;
import com.filmae.service.movie.MovieCatalogService;
import com.filmae.service.movie.MovieSearchCriteria;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Catálogo")
@RequiredArgsConstructor
public class MovieController {

    private final MovieCatalogService movieCatalogService;

    @Operation(summary = "Gêneros do catálogo (id = nome IMDb, name = português)")
    @GetMapping("/genres")
    public ApiResponse<List<GenreResponse>> genres() {
        return ApiResponse.of(movieCatalogService.listGenres());
    }

    @Operation(summary = "Busca paginada de filmes (envelope duplo: data.data + data.meta)")
    @GetMapping("/movies")
    public ApiResponse<PagedData<MovieListItemResponse>> search(
        @CurrentUser Long userId,
        @RequestParam(required = false) String q,
        @RequestParam(name = "genres", required = false) List<String> genres,
        @RequestParam(name = "genres[]", required = false) List<String> genresArray,
        @RequestParam(required = false) String type,
        @RequestParam(required = false) Integer yearFrom,
        @RequestParam(required = false) Integer yearTo,
        @RequestParam(required = false) BigDecimal minRating,
        @RequestParam(required = false) Integer page,
        @RequestParam(required = false) Integer size
    ) {
        MovieSearchCriteria criteria = new MovieSearchCriteria(
            q, GenreFilterParser.parse(genresArray, genres), type, yearFrom, yearTo, minRating, page, size);
        return ApiResponse.of(movieCatalogService.search(userId, criteria));
    }

    @Operation(summary = "Detalhes de um filme")
    @GetMapping("/movies/{tconst}")
    public ApiResponse<MovieDetailsResponse> details(@CurrentUser Long userId, @PathVariable String tconst) {
        return ApiResponse.of(movieCatalogService.details(userId, tconst));
    }
}
