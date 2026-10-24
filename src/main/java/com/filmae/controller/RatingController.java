package com.filmae.controller;

import com.filmae.dto.collection.CollectionMovieResponse;
import com.filmae.dto.common.ApiResponse;
import com.filmae.dto.rating.MovieRatingValueResponse;
import com.filmae.dto.rating.SaveMovieRatingRequest;
import com.filmae.security.CurrentUser;
import com.filmae.service.collection.RatingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me/ratings")
@Tag(name = "Avaliações")
@RequiredArgsConstructor
public class RatingController {

    private final RatingService ratingService;

    @Operation(summary = "Salva a nota do usuário (0.5 a 5, passo 0.5)")
    @PutMapping("/{tconst}")
    public ApiResponse<CollectionMovieResponse> save(@CurrentUser Long userId,
                                                     @PathVariable String tconst,
                                                     @Valid @RequestBody SaveMovieRatingRequest request) {
        return ApiResponse.of(ratingService.save(userId, tconst, request.getRating()));
    }

    @Operation(summary = "Nota do usuário para o filme ({rating: null} se não houver)")
    @GetMapping("/{tconst}")
    public ApiResponse<MovieRatingValueResponse> get(@CurrentUser Long userId, @PathVariable String tconst) {
        return ApiResponse.of(ratingService.get(userId, tconst));
    }

    @Operation(summary = "Remove a nota do usuário")
    @DeleteMapping("/{tconst}")
    public ApiResponse<CollectionMovieResponse> delete(@CurrentUser Long userId, @PathVariable String tconst) {
        return ApiResponse.of(ratingService.delete(userId, tconst));
    }
}
