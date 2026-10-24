package com.filmae.service;

import com.filmae.dto.common.ApiFieldError;
import com.filmae.dto.onboarding.CompleteOnboardingResponse;
import com.filmae.dto.onboarding.OnboardingMovieDto;
import com.filmae.dto.onboarding.OnboardingProgressResponse;
import com.filmae.dto.onboarding.SaveFavoriteMoviesRequest;
import com.filmae.dto.onboarding.SaveFavoriteMoviesResponse;
import com.filmae.dto.onboarding.SaveGenresRequest;
import com.filmae.dto.onboarding.SaveGenresResponse;
import com.filmae.dto.onboarding.SaveProfileStoryRequest;
import com.filmae.dto.onboarding.SaveProfileStoryResponse;
import com.filmae.entity.AlternativeTitle;
import com.filmae.entity.Movie;
import com.filmae.entity.OnboardingStep;
import com.filmae.entity.User;
import com.filmae.entity.UserOnboarding;
import com.filmae.entity.UserOnboardingFavoriteMovie;
import com.filmae.exception.ApiException;
import com.filmae.exception.ErrorCode;
import com.filmae.repository.AlternativeTitleRepository;
import com.filmae.repository.MovieRepository;
import com.filmae.repository.NotificationRepository;
import com.filmae.repository.UserOnboardingFavoriteMovieRepository;
import com.filmae.repository.UserOnboardingRepository;
import com.filmae.repository.UserRepository;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OnboardingService")
class OnboardingServiceTest {

    private static final Long USER_ID = 7L;

    @Mock private UserOnboardingRepository onboardingRepository;
    @Mock private UserOnboardingFavoriteMovieRepository favoriteMovieRepository;
    @Mock private UserRepository userRepository;
    @Mock private MovieRepository movieRepository;
    @Mock private AlternativeTitleRepository alternativeTitleRepository;
    @Mock private NotificationRepository notificationRepository;
    @Mock private GenreResolver genreResolver;
    @InjectMocks private OnboardingService service;

    private static User user(Instant completedAt) {
        return User.builder().id(USER_ID).authUserId(UUID.randomUUID()).email("a@b.com").name("Ana")
            .onboardingCompletedAt(completedAt).build();
    }

    private static UserOnboarding onboarding(OnboardingStep step, List<String> genres, String story) {
        UserOnboarding onboarding = UserOnboarding.emptyFor(USER_ID);
        onboarding.setCurrentStep(step);
        onboarding.setGenres(new ArrayList<>(genres));
        onboarding.setProfileStory(story);
        return onboarding;
    }

    private static Movie movie(String tconst, String title) {
        return Movie.builder().tconst(tconst).titleType("movie").primaryTitle(title).originalTitle(title).build();
    }

    private static AlternativeTitle brTitle(String tconst, int ordering, String title) {
        return AlternativeTitle.builder().titleId(tconst).ordering(ordering).title(title).region("BR").build();
    }

    private void givenOnboarding(UserOnboarding onboarding) {
        when(onboardingRepository.findById(USER_ID)).thenReturn(Optional.ofNullable(onboarding));
    }

    private void givenMovies(Movie... movies) {
        lenient().when(movieRepository.findAllById(anyCollection())).thenReturn(List.of(movies));
    }

    private static void assertApiError(ThrowingCallable call, ErrorCode code, String... fields) {
        assertThatThrownBy(call).isInstanceOfSatisfying(ApiException.class, ex -> {
            assertThat(ex.getCode()).isEqualTo(code);
            if (fields.length > 0) {
                assertThat(ex.getFields()).extracting(ApiFieldError::getField).containsExactly(fields);
            }
        });
    }

    @Nested
    @DisplayName("Regras de etapas")
    class StepRules {

        @Test
        @DisplayName("advance só avança, nunca volta")
        void advanceIsMonotonic() {
            assertThat(OnboardingService.advance(OnboardingStep.GENRES, OnboardingStep.FAVORITE_MOVIES))
                .isEqualTo(OnboardingStep.FAVORITE_MOVIES);
            assertThat(OnboardingService.advance(OnboardingStep.COMPLETE, OnboardingStep.FAVORITE_MOVIES))
                .isEqualTo(OnboardingStep.COMPLETE);
            assertThat(OnboardingService.advance(null, OnboardingStep.PROFILE_STORY))
                .isEqualTo(OnboardingStep.PROFILE_STORY);
        }

        @Test
        @DisplayName("currentStep exposto nunca é complete")
        void visibleStepHidesComplete() {
            assertThat(OnboardingService.visibleStep(OnboardingStep.COMPLETE)).isEqualTo(OnboardingStep.PROFILE_STORY);
            assertThat(OnboardingService.visibleStep(OnboardingStep.FAVORITE_MOVIES)).isEqualTo(OnboardingStep.FAVORITE_MOVIES);
            assertThat(OnboardingService.visibleStep(null)).isEqualTo(OnboardingStep.GENRES);
        }

        @Test
        @DisplayName("missingSteps lista as etapas pendentes na ordem do fluxo")
        void missingSteps() {
            assertThat(OnboardingService.missingSteps(UserOnboarding.emptyFor(USER_ID)))
                .containsExactly(OnboardingStep.GENRES, OnboardingStep.FAVORITE_MOVIES, OnboardingStep.PROFILE_STORY);
            assertThat(OnboardingService.missingSteps(onboarding(OnboardingStep.FAVORITE_MOVIES, List.of("Drama"), null)))
                .containsExactly(OnboardingStep.FAVORITE_MOVIES, OnboardingStep.PROFILE_STORY);
            assertThat(OnboardingService.missingSteps(onboarding(OnboardingStep.PROFILE_STORY, List.of("Drama"), null)))
                .containsExactly(OnboardingStep.PROFILE_STORY);
            assertThat(OnboardingService.missingSteps(onboarding(OnboardingStep.COMPLETE, List.of("Drama"), "")))
                .isEmpty();
        }
    }

    @Nested
    @DisplayName("GET progresso")
    class GetProgress {

        @Test
        @DisplayName("Sem registro: etapa genres, listas vazias e profileStory null")
        void emptyProgress() {
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(null)));
            givenOnboarding(null);
            when(favoriteMovieRepository.findByUserIdOrderByPositionAsc(USER_ID)).thenReturn(List.of());

            OnboardingProgressResponse progress = service.getProgress(USER_ID);

            assertThat(progress.completed()).isFalse();
            assertThat(progress.currentStep()).isEqualTo(OnboardingStep.GENRES);
            assertThat(progress.genres()).isEmpty();
            assertThat(progress.favoriteMovies()).isEmpty();
            assertThat(progress.profileStory()).isNull();
        }

        @Test
        @DisplayName("Favoritos com título BR quando houver e na ordem salva")
        void favoritesWithBrazilianTitles() {
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(Instant.now())));
            givenOnboarding(onboarding(OnboardingStep.COMPLETE, List.of("Sci-Fi"), "texto"));
            when(favoriteMovieRepository.findByUserIdOrderByPositionAsc(USER_ID)).thenReturn(List.of(
                UserOnboardingFavoriteMovie.builder().userId(USER_ID).tconst("tt0133093").position(0).build(),
                UserOnboardingFavoriteMovie.builder().userId(USER_ID).tconst("tt0816692").position(1).build()));
            givenMovies(movie("tt0816692", "Interstellar"), movie("tt0133093", "The Matrix"));
            when(alternativeTitleRepository.findByTitleIdInAndRegionOrderByTitleIdAscOrderingAsc(anyCollection(), eq("BR")))
                .thenReturn(List.of(brTitle("tt0816692", 3, "Interestelar"), brTitle("tt0816692", 9, "Interestelar (outro)")));

            OnboardingProgressResponse progress = service.getProgress(USER_ID);

            assertThat(progress.completed()).isTrue();
            assertThat(progress.currentStep()).isEqualTo(OnboardingStep.PROFILE_STORY);
            assertThat(progress.favoriteMovies()).containsExactly(
                new OnboardingMovieDto("tt0133093", "The Matrix"),
                new OnboardingMovieDto("tt0816692", "Interestelar"));
        }
    }

    @Nested
    @DisplayName("PUT genres")
    class SaveGenres {

        @Test
        @DisplayName("Lista nula, vazia ou grande demais gera VALIDATION_ERROR em genres")
        void invalidLists() {
            assertApiError(() -> service.saveGenres(USER_ID, new SaveGenresRequest(null)), ErrorCode.VALIDATION_ERROR, "genres");
            assertApiError(() -> service.saveGenres(USER_ID, new SaveGenresRequest(List.of())), ErrorCode.VALIDATION_ERROR, "genres");
            assertApiError(() -> service.saveGenres(USER_ID, new SaveGenresRequest(Collections.nCopies(31, "drama"))),
                ErrorCode.VALIDATION_ERROR, "genres");
            verify(onboardingRepository, never()).save(any());
        }

        @Test
        @DisplayName("Gênero inválido propaga o erro do resolver sem salvar")
        void invalidGenre() {
            when(genreResolver.resolveAll(anyList(), eq("genres"))).thenThrow(ApiException.validation("genres[0]", "Gênero inválido: x"));

            assertApiError(() -> service.saveGenres(USER_ID, new SaveGenresRequest(List.of("x"))), ErrorCode.VALIDATION_ERROR, "genres[0]");
            verify(onboardingRepository, never()).save(any());
        }

        @Test
        @DisplayName("Salva nomes do IMDb e avança para favoriteMovies")
        void savesImdbNames() {
            when(genreResolver.resolveAll(List.of("drama", "ficcao-cientifica"), "genres")).thenReturn(List.of("Drama", "Sci-Fi"));
            givenOnboarding(null);

            SaveGenresResponse response = service.saveGenres(USER_ID, new SaveGenresRequest(List.of("drama", "ficcao-cientifica")));

            ArgumentCaptor<UserOnboarding> saved = ArgumentCaptor.forClass(UserOnboarding.class);
            verify(onboardingRepository).save(saved.capture());
            assertThat(saved.getValue().getGenres()).containsExactly("Drama", "Sci-Fi");
            assertThat(saved.getValue().getCurrentStep()).isEqualTo(OnboardingStep.FAVORITE_MOVIES);
            assertThat(response.genres()).containsExactly("Drama", "Sci-Fi");
            assertThat(response.nextStep()).isEqualTo(OnboardingStep.FAVORITE_MOVIES);
        }

        @Test
        @DisplayName("Reeditar gêneros não volta o progresso")
        void reeditKeepsProgress() {
            when(genreResolver.resolveAll(anyList(), anyString())).thenReturn(List.of("Horror"));
            UserOnboarding existing = onboarding(OnboardingStep.COMPLETE, List.of("Drama"), "texto");
            givenOnboarding(existing);

            service.saveGenres(USER_ID, new SaveGenresRequest(List.of("terror")));

            assertThat(existing.getCurrentStep()).isEqualTo(OnboardingStep.COMPLETE);
            assertThat(existing.getGenres()).containsExactly("Horror");
        }
    }

    @Nested
    @DisplayName("PUT favorite-movies")
    class SaveFavorites {

        @Test
        @DisplayName("movieIds nulo ou acima do limite gera VALIDATION_ERROR")
        void invalidLists() {
            assertApiError(() -> service.saveFavoriteMovies(USER_ID, new SaveFavoriteMoviesRequest(null)),
                ErrorCode.VALIDATION_ERROR, "movieIds");
            assertApiError(() -> service.saveFavoriteMovies(USER_ID, new SaveFavoriteMoviesRequest(Collections.nCopies(21, "tt0816692"))),
                ErrorCode.VALIDATION_ERROR, "movieIds");
        }

        @Test
        @DisplayName("Formato inválido de tconst aponta o índice")
        void invalidFormat() {
            assertApiError(() -> service.saveFavoriteMovies(USER_ID,
                    new SaveFavoriteMoviesRequest(Arrays.asList("tt0816692", "interestelar", null))),
                ErrorCode.VALIDATION_ERROR, "movieIds[1]", "movieIds[2]");
        }

        @Test
        @DisplayName("tconst inexistente gera VALIDATION_ERROR")
        void unknownMovie() {
            givenMovies(movie("tt0816692", "Interstellar"));

            assertApiError(() -> service.saveFavoriteMovies(USER_ID, new SaveFavoriteMoviesRequest(List.of("tt0816692", "tt9999999"))),
                ErrorCode.VALIDATION_ERROR, "movieIds[1]");
            verify(favoriteMovieRepository, never()).deleteAllByUserId(anyLong());
        }

        @Test
        @DisplayName("Sem gêneros salvos responde 422 ONBOARDING_INCOMPLETE")
        void requiresGenres() {
            givenMovies(movie("tt0816692", "Interstellar"));
            givenOnboarding(null);

            assertApiError(() -> service.saveFavoriteMovies(USER_ID, new SaveFavoriteMoviesRequest(List.of("tt0816692"))),
                ErrorCode.ONBOARDING_INCOMPLETE);
        }

        @Test
        @DisplayName("Substitui os favoritos com posição, sem duplicatas, e avança para profileStory")
        @SuppressWarnings("unchecked")
        void replacesFavorites() {
            givenMovies(movie("tt0816692", "Interstellar"), movie("tt0133093", "The Matrix"));
            when(alternativeTitleRepository.findByTitleIdInAndRegionOrderByTitleIdAscOrderingAsc(anyCollection(), eq("BR")))
                .thenReturn(List.of(brTitle("tt0816692", 1, "Interestelar")));
            UserOnboarding existing = onboarding(OnboardingStep.FAVORITE_MOVIES, List.of("Drama"), null);
            givenOnboarding(existing);

            SaveFavoriteMoviesResponse response = service.saveFavoriteMovies(USER_ID,
                new SaveFavoriteMoviesRequest(List.of("tt0816692", " tt0133093", "tt0816692")));

            verify(favoriteMovieRepository).deleteAllByUserId(USER_ID);
            ArgumentCaptor<Collection<UserOnboardingFavoriteMovie>> saved = ArgumentCaptor.forClass(Collection.class);
            verify(favoriteMovieRepository).saveAll(saved.capture());
            assertThat(saved.getValue()).extracting(UserOnboardingFavoriteMovie::getTconst, UserOnboardingFavoriteMovie::getPosition)
                .containsExactly(tuple("tt0816692", 0), tuple("tt0133093", 1));
            assertThat(existing.getCurrentStep()).isEqualTo(OnboardingStep.PROFILE_STORY);
            assertThat(response.nextStep()).isEqualTo(OnboardingStep.PROFILE_STORY);
            assertThat(response.favoriteMovies()).containsExactly(
                new OnboardingMovieDto("tt0816692", "Interestelar"),
                new OnboardingMovieDto("tt0133093", "The Matrix"));
        }

        @Test
        @DisplayName("Lista vazia é aceita (etapa pulada) e avança")
        void emptyListAccepted() {
            UserOnboarding existing = onboarding(OnboardingStep.FAVORITE_MOVIES, List.of("Drama"), null);
            givenOnboarding(existing);

            SaveFavoriteMoviesResponse response = service.saveFavoriteMovies(USER_ID, new SaveFavoriteMoviesRequest(List.of()));

            verify(favoriteMovieRepository).deleteAllByUserId(USER_ID);
            assertThat(response.favoriteMovies()).isEmpty();
            assertThat(existing.getCurrentStep()).isEqualTo(OnboardingStep.PROFILE_STORY);
        }
    }

    @Nested
    @DisplayName("PUT profile-story")
    class SaveProfileStory {

        @Test
        @DisplayName("text nulo ou acima de 400 caracteres gera VALIDATION_ERROR em text")
        void invalidText() {
            assertApiError(() -> service.saveProfileStory(USER_ID, new SaveProfileStoryRequest(null)), ErrorCode.VALIDATION_ERROR, "text");
            assertApiError(() -> service.saveProfileStory(USER_ID, new SaveProfileStoryRequest("a".repeat(401))),
                ErrorCode.VALIDATION_ERROR, "text");
        }

        @Test
        @DisplayName("Antes de salvar favoritos responde 422 ONBOARDING_INCOMPLETE")
        void requiresFavoritesStep() {
            givenOnboarding(onboarding(OnboardingStep.FAVORITE_MOVIES, List.of("Drama"), null));

            assertApiError(() -> service.saveProfileStory(USER_ID, new SaveProfileStoryRequest("oi")), ErrorCode.ONBOARDING_INCOMPLETE);
        }

        @Test
        @DisplayName("Salva o texto aparado e avança para complete; texto vazio é aceito")
        void savesStory() {
            UserOnboarding existing = onboarding(OnboardingStep.PROFILE_STORY, List.of("Drama"), null);
            givenOnboarding(existing);

            SaveProfileStoryResponse response = service.saveProfileStory(USER_ID, new SaveProfileStoryRequest("  Gosto de terror.  "));
            assertThat(response.profileStory()).isEqualTo("Gosto de terror.");
            assertThat(response.nextStep()).isEqualTo(OnboardingStep.COMPLETE);
            assertThat(existing.getCurrentStep()).isEqualTo(OnboardingStep.COMPLETE);

            assertThat(service.saveProfileStory(USER_ID, new SaveProfileStoryRequest("")).profileStory()).isEmpty();
            assertThat(existing.getProfileStory()).isEmpty();
        }
    }

    @Nested
    @DisplayName("POST complete")
    class Complete {

        @Test
        @DisplayName("Etapas faltando geram 422 ONBOARDING_INCOMPLETE com as etapas em fields")
        void incomplete() {
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(null)));
            givenOnboarding(onboarding(OnboardingStep.PROFILE_STORY, List.of("Drama"), null));

            assertThatThrownBy(() -> service.complete(USER_ID)).isInstanceOfSatisfying(ApiException.class, ex -> {
                assertThat(ex.getCode()).isEqualTo(ErrorCode.ONBOARDING_INCOMPLETE);
                assertThat(ex.getStatus().value()).isEqualTo(422);
                assertThat(ex.getFields()).extracting(ApiFieldError::getField).containsExactly("profileStory");
            });
            verify(userRepository, never()).save(any());
            verify(notificationRepository, never()).insertIfAbsent(anyLong(), anyString(), anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("Sem nenhum progresso, todas as etapas aparecem como pendentes")
        void nothingDone() {
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(null)));
            givenOnboarding(null);

            assertApiError(() -> service.complete(USER_ID), ErrorCode.ONBOARDING_INCOMPLETE, "genres", "favoriteMovies", "profileStory");
        }

        @Test
        @DisplayName("Conclui: marca onboarding_completed_at e cria notificação onboarding com dedupe")
        void completes() {
            User user = user(null);
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            givenOnboarding(onboarding(OnboardingStep.COMPLETE, List.of("Drama"), ""));

            CompleteOnboardingResponse response = service.complete(USER_ID);

            assertThat(response.completed()).isTrue();
            assertThat(response.nextScreen()).isEqualTo("home");
            assertThat(response.completedAt()).isNotNull().isEqualTo(user.getOnboardingCompletedAt());
            assertThat(response.completedAt().getNano()).isZero();
            verify(userRepository).save(user);
            verify(notificationRepository).insertIfAbsent(eq(USER_ID), eq("onboarding"), anyString(), anyString(),
                eq(OnboardingService.COMPLETION_DEDUPE_KEY));
        }

        @Test
        @DisplayName("Já concluído: idempotente, devolve a data original sem nova notificação")
        void alreadyCompleted() {
            Instant completedAt = Instant.parse("2026-10-02T16:00:00Z");
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(completedAt)));

            CompleteOnboardingResponse response = service.complete(USER_ID);

            assertThat(response.completedAt()).isEqualTo(completedAt);
            verify(notificationRepository, never()).insertIfAbsent(anyLong(), anyString(), anyString(), anyString(), anyString());
        }
    }
}
