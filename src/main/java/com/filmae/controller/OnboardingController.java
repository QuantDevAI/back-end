package com.filmae.controller;

import com.filmae.dto.common.ApiResponse;
import com.filmae.dto.onboarding.CompleteOnboardingResponse;
import com.filmae.dto.onboarding.OnboardingProgressResponse;
import com.filmae.dto.onboarding.SaveFavoriteMoviesRequest;
import com.filmae.dto.onboarding.SaveFavoriteMoviesResponse;
import com.filmae.dto.onboarding.SaveGenresRequest;
import com.filmae.dto.onboarding.SaveGenresResponse;
import com.filmae.dto.onboarding.SaveProfileStoryRequest;
import com.filmae.dto.onboarding.SaveProfileStoryResponse;
import com.filmae.security.CurrentUser;
import com.filmae.service.OnboardingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me/onboarding")
@RequiredArgsConstructor
@Tag(name = "Onboarding")
public class OnboardingController {

    private final OnboardingService onboardingService;

    @Operation(summary = "Progresso do onboarding")
    @GetMapping
    public ResponseEntity<ApiResponse<OnboardingProgressResponse>> getProgress(@CurrentUser Long userId) {
        return ApiResponse.ok(onboardingService.getProgress(userId));
    }

    @Operation(summary = "Salva os gêneros (slug do front ou nome do IMDb)")
    @PutMapping("/genres")
    public ResponseEntity<ApiResponse<SaveGenresResponse>> saveGenres(@CurrentUser Long userId,
                                                                      @RequestBody SaveGenresRequest request) {
        return ApiResponse.ok(onboardingService.saveGenres(userId, request));
    }

    @Operation(summary = "Salva os filmes favoritos (tconsts)")
    @PutMapping("/favorite-movies")
    public ResponseEntity<ApiResponse<SaveFavoriteMoviesResponse>> saveFavoriteMovies(@CurrentUser Long userId,
                                                                                      @RequestBody SaveFavoriteMoviesRequest request) {
        return ApiResponse.ok(onboardingService.saveFavoriteMovies(userId, request));
    }

    @Operation(summary = "Salva o texto livre do perfil")
    @PutMapping("/profile-story")
    public ResponseEntity<ApiResponse<SaveProfileStoryResponse>> saveProfileStory(@CurrentUser Long userId,
                                                                                  @RequestBody SaveProfileStoryRequest request) {
        return ApiResponse.ok(onboardingService.saveProfileStory(userId, request));
    }

    @Operation(summary = "Conclui o onboarding (422 ONBOARDING_INCOMPLETE se faltar etapa)")
    @PostMapping("/complete")
    public ResponseEntity<ApiResponse<CompleteOnboardingResponse>> complete(@CurrentUser Long userId) {
        return ApiResponse.ok(onboardingService.complete(userId));
    }
}
