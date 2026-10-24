package com.filmae.service;

import com.filmae.dto.profile.UpdateUserPreferencesRequest;
import com.filmae.dto.profile.UpdateUserProfileRequest;
import com.filmae.dto.profile.UserPreferencesResponse;
import com.filmae.dto.profile.UserProfileResponse;
import com.filmae.entity.User;
import com.filmae.entity.UserPreferences;
import com.filmae.exception.ApiException;
import com.filmae.exception.ErrorCode;
import com.filmae.repository.UserPreferencesRepository;
import com.filmae.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProfileService")
class ProfileServiceTest {

    private static final Long USER_ID = 1L;

    @Mock private UserRepository userRepository;
    @Mock private UserPreferencesRepository userPreferencesRepository;

    @InjectMocks private ProfileService service;

    @Test
    @DisplayName("bio null na entidade sai como string vazia")
    void bioIsNeverNull() {
        User user = user("Pedro", null);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        UserProfileResponse profile = service.getProfile(USER_ID);

        assertThat(profile.bio()).isEqualTo("");
        assertThat(profile.email()).isEqualTo("pedro@example.com");
    }

    @Test
    @DisplayName("PATCH só com name mantém a bio")
    void patchOnlyName() {
        User user = user("Pedro", "Bio antiga");
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(userRepository.saveAndFlush(user)).thenReturn(user);

        UserProfileResponse profile = service.updateProfile(USER_ID, new UpdateUserProfileRequest("  Pedro Silva ", null));

        assertThat(profile.name()).isEqualTo("Pedro Silva");
        assertThat(profile.bio()).isEqualTo("Bio antiga");
    }

    @Test
    @DisplayName("PATCH só com bio mantém o nome; bio vazia é permitida")
    void patchOnlyBio() {
        User user = user("Pedro", "Bio antiga");
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(userRepository.saveAndFlush(user)).thenReturn(user);

        UserProfileResponse profile = service.updateProfile(USER_ID, new UpdateUserProfileRequest(null, "   "));

        assertThat(profile.name()).isEqualTo("Pedro");
        assertThat(profile.bio()).isEqualTo("");
    }

    @Test
    @DisplayName("PATCH com name em branco responde VALIDATION_ERROR no campo name")
    void blankNameIsRejected() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user("Pedro", "")));

        assertThatThrownBy(() -> service.updateProfile(USER_ID, new UpdateUserProfileRequest("   ", null)))
            .isInstanceOf(ApiException.class)
            .satisfies(ex -> {
                ApiException api = (ApiException) ex;
                assertThat(api.getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                assertThat(api.getFields()).singleElement().satisfies(f -> assertThat(f.getField()).isEqualTo("name"));
            });
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("PATCH de preferências é parcial")
    void patchPreferencesPartially() {
        UserPreferences prefs = UserPreferences.defaultsFor(USER_ID);
        when(userPreferencesRepository.findById(USER_ID)).thenReturn(Optional.of(prefs));
        when(userPreferencesRepository.saveAndFlush(prefs)).thenReturn(prefs);

        UserPreferencesResponse response = service.updatePreferences(USER_ID, new UpdateUserPreferencesRequest(null, true));

        assertThat(response.receiveRecommendationsByEmail()).isTrue();
        assertThat(response.spoilerModeEnabled()).isTrue();
    }

    @Test
    @DisplayName("GET de preferências cria os defaults se a linha não existir")
    void preferencesDefaultsWhenMissing() {
        when(userPreferencesRepository.findById(USER_ID)).thenReturn(Optional.empty());
        when(userPreferencesRepository.save(any(UserPreferences.class))).thenAnswer(inv -> inv.getArgument(0));

        UserPreferencesResponse response = service.getPreferences(USER_ID);

        assertThat(response).isEqualTo(new UserPreferencesResponse(true, false));
    }

    private static User user(String name, String bio) {
        return User.builder().id(USER_ID).authUserId(UUID.randomUUID()).email("pedro@example.com")
            .name(name).bio(bio).createdAt(Instant.parse("2026-09-20T17:00:00Z")).build();
    }
}
