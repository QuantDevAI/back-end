package com.filmae.controller;

import com.filmae.dto.common.ApiResponse;
import com.filmae.dto.profile.UpdateUserPreferencesRequest;
import com.filmae.dto.profile.UpdateUserProfileRequest;
import com.filmae.dto.profile.UserPreferencesResponse;
import com.filmae.dto.profile.UserProfileResponse;
import com.filmae.security.CurrentUser;
import com.filmae.service.ProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me")
@Tag(name = "Perfil")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;

    @Operation(summary = "Dados do perfil do usuário corrente")
    @GetMapping
    public ApiResponse<UserProfileResponse> getProfile(@CurrentUser Long userId) {
        return ApiResponse.of(profileService.getProfile(userId));
    }

    @Operation(summary = "Atualiza nome e/ou bio (parcial); o e-mail não é alterável")
    @PatchMapping
    public ApiResponse<UserProfileResponse> updateProfile(@CurrentUser Long userId,
                                                          @Valid @RequestBody UpdateUserProfileRequest request) {
        return ApiResponse.of(profileService.updateProfile(userId, request));
    }

    @Operation(summary = "Preferências do perfil")
    @GetMapping("/preferences")
    public ApiResponse<UserPreferencesResponse> getPreferences(@CurrentUser Long userId) {
        return ApiResponse.of(profileService.getPreferences(userId));
    }

    @Operation(summary = "Atualiza preferências (parcial)")
    @PatchMapping("/preferences")
    public ApiResponse<UserPreferencesResponse> updatePreferences(@CurrentUser Long userId,
                                                                  @RequestBody UpdateUserPreferencesRequest request) {
        return ApiResponse.of(profileService.updatePreferences(userId, request));
    }
}
