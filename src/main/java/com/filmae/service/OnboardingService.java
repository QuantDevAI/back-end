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
import com.filmae.entity.NotificationType;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Onboarding em etapas: {@code genres → favoriteMovies → profileStory → complete}.
 *
 * <p>{@code user_onboarding.current_step} guarda a próxima etapa pendente e só avança (reeditar uma etapa
 * anterior não volta o progresso). Cada PUT exige a etapa anterior salva ({@code 422 ONBOARDING_INCOMPLETE}).
 * Filmes favoritos podem ser uma lista vazia e o texto do perfil pode ser {@code ""}, porque o front deixa
 * pular essas etapas; o que conta é a etapa ter sido salva.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class OnboardingService {

    public static final int MAX_GENRES = 30;
    public static final int MAX_FAVORITE_MOVIES = 20;
    public static final int MAX_PROFILE_STORY_LENGTH = 400;
    static final String COMPLETION_DEDUPE_KEY = "onboarding:completed";
    static final String COMPLETION_TITLE = "Perfil criado!";
    static final String COMPLETION_MESSAGE =
        "Seu perfil cinéfilo está pronto. Já estamos preparando recomendações com base nos seus gostos.";

    private static final Pattern TCONST = Pattern.compile("^tt\\d{7,}$");

    private final UserOnboardingRepository onboardingRepository;
    private final UserOnboardingFavoriteMovieRepository favoriteMovieRepository;
    private final UserRepository userRepository;
    private final MovieRepository movieRepository;
    private final AlternativeTitleRepository alternativeTitleRepository;
    private final NotificationRepository notificationRepository;
    private final GenreResolver genreResolver;

    @Transactional(readOnly = true)
    public OnboardingProgressResponse getProgress(Long userId) {
        User user = requireUser(userId);
        UserOnboarding onboarding = onboardingRepository.findById(userId).orElseGet(() -> UserOnboarding.emptyFor(userId));
        List<String> favoriteIds = favoriteMovieRepository.findByUserIdOrderByPositionAsc(userId).stream()
            .map(UserOnboardingFavoriteMovie::getTconst)
            .toList();
        return new OnboardingProgressResponse(
            user.isOnboardingCompleted(),
            visibleStep(onboarding.getCurrentStep()),
            List.copyOf(onboarding.getGenres()),
            toMovies(favoriteIds),
            onboarding.getProfileStory()
        );
    }

    public SaveGenresResponse saveGenres(Long userId, SaveGenresRequest request) {
        List<String> input = request != null ? request.genres() : null;
        if (input == null || input.isEmpty()) {
            throw ApiException.validation("genres", "Selecione pelo menos um gênero");
        }
        if (input.size() > MAX_GENRES) {
            throw ApiException.validation("genres", "Selecione no máximo " + MAX_GENRES + " gêneros");
        }
        List<String> imdbGenres = genreResolver.resolveAll(input, "genres");

        UserOnboarding onboarding = loadOrCreate(userId);
        onboarding.setGenres(new ArrayList<>(imdbGenres));
        onboarding.setCurrentStep(advance(onboarding.getCurrentStep(), OnboardingStep.FAVORITE_MOVIES));
        onboardingRepository.save(onboarding);
        return new SaveGenresResponse(imdbGenres, OnboardingStep.FAVORITE_MOVIES);
    }

    public SaveFavoriteMoviesResponse saveFavoriteMovies(Long userId, SaveFavoriteMoviesRequest request) {
        List<String> input = request != null ? request.movieIds() : null;
        if (input == null) {
            throw ApiException.validation("movieIds", "Informe a lista de filmes (pode ser vazia)");
        }
        if (input.size() > MAX_FAVORITE_MOVIES) {
            throw ApiException.validation("movieIds", "Selecione no máximo " + MAX_FAVORITE_MOVIES + " filmes");
        }
        List<String> movieIds = validateMovieIds(input);

        UserOnboarding onboarding = loadOrCreate(userId);
        if (onboarding.getGenres().isEmpty()) {
            throw ApiException.onboardingIncomplete("Salve os gêneros antes dos filmes favoritos");
        }

        favoriteMovieRepository.deleteAllByUserId(userId);
        List<UserOnboardingFavoriteMovie> favorites = new ArrayList<>();
        for (int i = 0; i < movieIds.size(); i++) {
            favorites.add(UserOnboardingFavoriteMovie.builder().userId(userId).tconst(movieIds.get(i)).position(i).build());
        }
        favoriteMovieRepository.saveAll(favorites);

        onboarding.setCurrentStep(advance(onboarding.getCurrentStep(), OnboardingStep.PROFILE_STORY));
        onboardingRepository.save(onboarding);
        return new SaveFavoriteMoviesResponse(toMovies(movieIds), OnboardingStep.PROFILE_STORY);
    }

    public SaveProfileStoryResponse saveProfileStory(Long userId, SaveProfileStoryRequest request) {
        String text = request != null ? request.text() : null;
        if (text == null) {
            throw ApiException.validation("text", "Informe o texto do perfil (pode ser vazio)");
        }
        String story = text.trim();
        if (story.length() > MAX_PROFILE_STORY_LENGTH) {
            throw ApiException.validation("text", "O texto deve ter no máximo " + MAX_PROFILE_STORY_LENGTH + " caracteres");
        }

        UserOnboarding onboarding = loadOrCreate(userId);
        if (onboarding.getCurrentStep().ordinal() < OnboardingStep.PROFILE_STORY.ordinal()) {
            throw ApiException.onboardingIncomplete("Salve os gêneros e os filmes favoritos antes do texto do perfil");
        }
        onboarding.setProfileStory(story);
        onboarding.setCurrentStep(advance(onboarding.getCurrentStep(), OnboardingStep.COMPLETE));
        onboardingRepository.save(onboarding);
        return new SaveProfileStoryResponse(story, OnboardingStep.COMPLETE);
    }

    /** Idempotente: se já concluído, devolve a data original sem criar outra notificação. */
    public CompleteOnboardingResponse complete(Long userId) {
        User user = requireUser(userId);
        if (user.isOnboardingCompleted()) {
            return CompleteOnboardingResponse.of(user.getOnboardingCompletedAt());
        }

        UserOnboarding onboarding = onboardingRepository.findById(userId).orElseGet(() -> UserOnboarding.emptyFor(userId));
        List<OnboardingStep> missing = missingSteps(onboarding);
        if (!missing.isEmpty()) {
            List<ApiFieldError> fields = missing.stream()
                .map(step -> ApiFieldError.of(step.getValue(), "Etapa pendente"))
                .toList();
            String names = missing.stream().map(OnboardingStep::getValue).collect(Collectors.joining(", "));
            throw new ApiException(ErrorCode.ONBOARDING_INCOMPLETE, "Conclua as etapas pendentes do onboarding: " + names, fields);
        }

        Instant completedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        user.setOnboardingCompletedAt(completedAt);
        userRepository.save(user);
        notificationRepository.insertIfAbsent(userId, NotificationType.ONBOARDING.getValue(),
            COMPLETION_TITLE, COMPLETION_MESSAGE, COMPLETION_DEDUPE_KEY);
        return CompleteOnboardingResponse.of(completedAt);
    }

    /** Etapas que ainda faltam para concluir, na ordem do fluxo. */
    static List<OnboardingStep> missingSteps(UserOnboarding onboarding) {
        List<OnboardingStep> missing = new ArrayList<>();
        if (onboarding.getGenres().isEmpty()) {
            missing.add(OnboardingStep.GENRES);
        }
        if (onboarding.getCurrentStep().ordinal() < OnboardingStep.PROFILE_STORY.ordinal()) {
            missing.add(OnboardingStep.FAVORITE_MOVIES);
        }
        if (onboarding.getProfileStory() == null) {
            missing.add(OnboardingStep.PROFILE_STORY);
        }
        return missing;
    }

    /** O progresso só avança: salvar de novo uma etapa anterior não volta {@code currentStep}. */
    static OnboardingStep advance(OnboardingStep current, OnboardingStep target) {
        if (current == null) {
            return target;
        }
        return target.ordinal() > current.ordinal() ? target : current;
    }

    static OnboardingStep visibleStep(OnboardingStep step) {
        if (step == null) {
            return OnboardingStep.GENRES;
        }
        return step == OnboardingStep.COMPLETE ? OnboardingStep.PROFILE_STORY : step;
    }

    /** Valida formato e existência dos tconsts; remove duplicatas mantendo a ordem. */
    private List<String> validateMovieIds(List<String> input) {
        List<ApiFieldError> errors = new ArrayList<>();
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        for (int i = 0; i < input.size(); i++) {
            String id = input.get(i) != null ? input.get(i).trim() : null;
            if (id == null || !TCONST.matcher(id).matches()) {
                errors.add(ApiFieldError.of("movieIds[" + i + "]", "Identificador de filme inválido"));
            } else {
                ids.add(id);
            }
        }
        if (errors.isEmpty() && !ids.isEmpty()) {
            Map<String, Movie> existing = movieRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Movie::getTconst, Function.identity()));
            for (int i = 0; i < input.size(); i++) {
                if (!existing.containsKey(input.get(i).trim())) {
                    errors.add(ApiFieldError.of("movieIds[" + i + "]", "Filme não encontrado: " + input.get(i).trim()));
                }
            }
        }
        if (!errors.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Há filmes inválidos", errors);
        }
        return List.copyOf(ids);
    }

    /** {@code {id, title}} na ordem recebida; título BR (menor ordering) quando houver, senão o título principal. */
    private List<OnboardingMovieDto> toMovies(List<String> tconsts) {
        if (tconsts.isEmpty()) {
            return List.of();
        }
        Map<String, String> primaryTitles = movieRepository.findAllById(tconsts).stream()
            .collect(Collectors.toMap(Movie::getTconst, Movie::getPrimaryTitle));
        Map<String, String> brTitles = new HashMap<>();
        alternativeTitleRepository.findByTitleIdInAndRegionOrderByTitleIdAscOrderingAsc(tconsts, AlternativeTitle.REGION_BR)
            .forEach(title -> brTitles.putIfAbsent(title.getTitleId(), title.getTitle()));
        return tconsts.stream()
            .filter(primaryTitles::containsKey)
            .map(id -> new OnboardingMovieDto(id, brTitles.getOrDefault(id, primaryTitles.get(id))))
            .toList();
    }

    private UserOnboarding loadOrCreate(Long userId) {
        return onboardingRepository.findById(userId).orElseGet(() -> UserOnboarding.emptyFor(userId));
    }

    private User requireUser(Long userId) {
        return userRepository.findById(userId).orElseThrow(() -> ApiException.notFound("Usuário não encontrado"));
    }
}
