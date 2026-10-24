package com.filmae.service;

import com.filmae.dto.profile.UpdateUserPreferencesRequest;
import com.filmae.dto.profile.UpdateUserProfileRequest;
import com.filmae.dto.profile.UserPreferencesResponse;
import com.filmae.dto.profile.UserProfileResponse;
import com.filmae.entity.User;
import com.filmae.entity.UserPreferences;
import com.filmae.exception.ApiException;
import com.filmae.repository.UserPreferencesRepository;
import com.filmae.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Perfil ({@code name}, {@code bio}) e preferências do usuário corrente. */
@Service
@Transactional
@RequiredArgsConstructor
public class ProfileService {

    private final UserRepository userRepository;
    private final UserPreferencesRepository userPreferencesRepository;

    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(Long userId) {
        return UserProfileResponse.from(loadUser(userId));
    }

    /** {@code name} é aparado e não pode ficar vazio; {@code bio} é aparada e pode ficar vazia. */
    public UserProfileResponse updateProfile(Long userId, UpdateUserProfileRequest request) {
        User user = loadUser(userId);
        if (request != null && request.name() != null) {
            String name = request.name().strip();
            if (name.isEmpty()) {
                throw ApiException.validation("name", "O nome não pode ficar vazio");
            }
            user.setName(name);
        }
        if (request != null && request.bio() != null) {
            user.setBio(request.bio().strip());
        }
        return UserProfileResponse.from(userRepository.saveAndFlush(user));
    }

    public UserPreferencesResponse getPreferences(Long userId) {
        return UserPreferencesResponse.from(loadPreferences(userId));
    }

    public UserPreferencesResponse updatePreferences(Long userId, UpdateUserPreferencesRequest request) {
        UserPreferences preferences = loadPreferences(userId);
        if (request != null && request.receiveRecommendationsByEmail() != null) {
            preferences.setReceiveRecommendationsByEmail(request.receiveRecommendationsByEmail());
        }
        if (request != null && request.spoilerModeEnabled() != null) {
            preferences.setSpoilerModeEnabled(request.spoilerModeEnabled());
        }
        return UserPreferencesResponse.from(userPreferencesRepository.saveAndFlush(preferences));
    }

    private User loadUser(Long userId) {
        return userRepository.findById(userId)
            .orElseThrow(() -> ApiException.notFound("Usuário não encontrado"));
    }

    private UserPreferences loadPreferences(Long userId) {
        return userPreferencesRepository.findById(userId)
            .orElseGet(() -> userPreferencesRepository.save(UserPreferences.defaultsFor(userId)));
    }
}
