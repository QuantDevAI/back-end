package com.filmae.repository;

import com.filmae.entity.GenreCatalog;
import com.filmae.entity.Movie;
import com.filmae.entity.MoviePrincipal;
import com.filmae.entity.Notification;
import com.filmae.entity.NotificationType;
import com.filmae.entity.OnboardingStep;
import com.filmae.entity.User;
import com.filmae.entity.UserMovieId;
import com.filmae.entity.UserMovieRating;
import com.filmae.entity.UserMovieState;
import com.filmae.entity.UserOnboarding;
import com.filmae.repository.projection.CollectionCountsView;
import com.filmae.repository.projection.GenreCountView;
import com.filmae.service.CurrentUserService;
import com.filmae.support.AbstractIntegrationTest;
import com.filmae.support.CatalogFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Migrations, entidades e queries dos repositories")
class PersistenceIntegrationTest extends AbstractIntegrationTest {

    @Autowired private CurrentUserService currentUserService;
    @Autowired private UserRepository userRepository;
    @Autowired private UserPreferencesRepository userPreferencesRepository;
    @Autowired private UserOnboardingRepository userOnboardingRepository;
    @Autowired private MovieRepository movieRepository;
    @Autowired private MoviePrincipalRepository moviePrincipalRepository;
    @Autowired private AlternativeTitleRepository alternativeTitleRepository;
    @Autowired private GenreCatalogRepository genreCatalogRepository;
    @Autowired private UserMovieStateRepository userMovieStateRepository;
    @Autowired private UserMovieRatingRepository userMovieRatingRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private TransactionTemplate transactionTemplate;

    private CatalogFixtures catalog;

    @BeforeEach
    void setUp() {
        cleanUserData();
        cleanCatalog();
        catalog = new CatalogFixtures(jdbcTemplate);
        catalog.movie("tt0816692").title("Interstellar").titlePt("Interestelar").year(2014).runtime(169)
            .genres("Adventure", "Drama", "Sci-Fi").rating("8.7", 2_200_000)
            .director("nm0634240", "Christopher Nolan").cast("Matthew McConaughey", "Anne Hathaway").insert();
        catalog.movie("tt0081505").title("The Shining").titlePt("O Iluminado").year(1980).runtime(146)
            .genres("Drama", "Horror").rating("8.4", 1_100_000).insert();
        catalog.movie("tt0114709").title("Toy Story").year(1995).runtime(81)
            .genres("Animation", "Comedy").rating("8.3", 1_000_000).insert();
    }

    @Test
    @DisplayName("CurrentUserService cria o usuário local sob demanda, com preferências, e é idempotente")
    void findOrCreateUser() {
        UUID authId = UUID.randomUUID();
        User created = transactionTemplate.execute(s -> currentUserService.findOrCreate(authId, "ana@example.com", "Ana"));
        User again = transactionTemplate.execute(s -> currentUserService.findOrCreate(authId, "ana@example.com", "Outro"));

        assertThat(created.getId()).isNotNull();
        assertThat(again.getId()).isEqualTo(created.getId());
        assertThat(again.getName()).isEqualTo("Ana");
        assertThat(again.getBio()).isEmpty();
        assertThat(again.getCreatedAt()).isNotNull();
        assertThat(userRepository.count()).isEqualTo(1);
        assertThat(userPreferencesRepository.findById(created.getId()))
            .hasValueSatisfying(p -> {
                assertThat(p.isReceiveRecommendationsByEmail()).isTrue();
                assertThat(p.isSpoilerModeEnabled()).isFalse();
            });
    }

    @Test
    @DisplayName("genre_catalog tem os slugs do front e mapeia para nomes IMDb")
    void genreCatalogSeed() {
        assertThat(genreCatalogRepository.findById("ficcao-cientifica")).map(GenreCatalog::getImdbName).hasValue("Sci-Fi");
        assertThat(genreCatalogRepository.findById("suspense")).hasValueSatisfying(g -> {
            assertThat(g.getImdbName()).isEqualTo("Thriller");
            assertThat(g.isCanonical()).isFalse();
        });
        assertThat(genreCatalogRepository.findBySlugIn(List.of("acao", "aventura", "animacao", "comedia", "crime",
            "documentario", "drama", "fantasia", "ficcao-cientifica", "terror", "romance", "suspense", "thriller",
            "misterio"))).hasSize(14);
        assertThat(genreCatalogRepository.findFirstByImdbNameIgnoreCaseAndCanonicalTrue("thriller"))
            .map(GenreCatalog::getSlug).hasValue("thriller");
    }

    @Test
    @DisplayName("Catálogo: arrays text[], jsonb, busca, contagem por gênero e candidatos")
    void catalogQueries() {
        Movie interstellar = movieRepository.findById("tt0816692").orElseThrow();
        assertThat(interstellar.getGenres()).containsExactly("Adventure", "Drama", "Sci-Fi");

        List<MoviePrincipal> principals = moviePrincipalRepository.findByTconstInOrderByTconstAscOrderingAsc(List.of("tt0816692"));
        assertThat(principals).extracting(MoviePrincipal::getCategory).containsExactly("director", "actor", "actor");

        assertThat(alternativeTitleRepository.findByTitleIdInAndRegionOrderByTitleIdAscOrderingAsc(List.of("tt0816692"), "BR"))
            .extracting(t -> t.getTitle()).containsExactly("Interestelar");

        Page<Movie> byPtTitle = movieRepository.search("iluminado", null, null, null, null, null, PageRequest.of(0, 10));
        assertThat(byPtTitle.getContent()).extracting(Movie::getTconst).containsExactly("tt0081505");

        Page<Movie> byGenre = movieRepository.search(null, "Horror,Animation", "movie", 1980, 2000,
            new BigDecimal("8.0"), PageRequest.of(0, 10));
        assertThat(byGenre.getTotalElements()).isEqualTo(2);
        assertThat(byGenre.getContent()).extracting(Movie::getTconst).containsExactly("tt0081505", "tt0114709");

        Page<Movie> all = movieRepository.search(null, null, null, null, null, null, PageRequest.of(0, 2));
        assertThat(all.getTotalElements()).isEqualTo(3);
        assertThat(all.getContent()).extracting(Movie::getTconst).containsExactly("tt0816692", "tt0081505");

        List<GenreCountView> counts = movieRepository.countMoviesByGenre();
        assertThat(counts).filteredOn(c -> c.getGenre().equals("Drama")).singleElement()
            .satisfies(c -> assertThat(c.getMovieCount()).isEqualTo(2L));

        User user = transactionTemplate.execute(s -> currentUserService.findOrCreate(UUID.randomUUID(), "b@example.com", "B"));
        userMovieStateRepository.save(UserMovieState.builder().userId(user.getId()).tconst("tt0816692")
            .watched(true).watchedAt(Instant.now()).build());

        List<Movie> candidates = movieRepository.findRecommendationCandidates(user.getId(), "Drama,Animation", "Horror", 0, 10);
        assertThat(candidates).extracting(Movie::getTconst).containsExactly("tt0114709");
    }

    @Test
    @DisplayName("Coleção, notas (CHECK de escala), onboarding e notificações")
    void userDataQueries() {
        User user = transactionTemplate.execute(s -> currentUserService.findOrCreate(UUID.randomUUID(), "c@example.com", "C"));
        Long userId = user.getId();

        userMovieStateRepository.save(UserMovieState.builder().userId(userId).tconst("tt0816692")
            .watched(true).favorite(true).watchedAt(Instant.parse("2026-10-01T10:00:00Z")).build());
        userMovieStateRepository.save(UserMovieState.builder().userId(userId).tconst("tt0081505")
            .watched(true).watchedAt(Instant.parse("2026-10-02T10:00:00Z")).build());
        userMovieStateRepository.save(UserMovieState.builder().userId(userId).tconst("tt0114709").watchlist(true).build());

        CollectionCountsView counts = userMovieStateRepository.countCollection(userId);
        assertThat(counts.getTotal()).isEqualTo(3L);
        assertThat(counts.getWatched()).isEqualTo(2L);
        assertThat(counts.getWatchlist()).isEqualTo(1L);
        assertThat(counts.getFavorite()).isEqualTo(1L);
        assertThat(counts.getRewatch()).isZero();
        assertThat(userMovieStateRepository.sumWatchedRuntimeMinutes(userId)).isEqualTo(169 + 146);
        assertThat(userMovieStateRepository.findWatchHistory(userId, PageRequest.of(0, 5)))
            .extracting(UserMovieState::getTconst).containsExactly("tt0081505", "tt0816692");
        assertThat(userMovieStateRepository.findTopWatchedGenres(userId, 1))
            .singleElement().satisfies(g -> {
                assertThat(g.getGenre()).isEqualTo("Drama");
                assertThat(g.getMovieCount()).isEqualTo(2L);
            });
        assertThat(userMovieStateRepository.findCollection(userId)).hasSize(3);

        userMovieRatingRepository.save(UserMovieRating.builder().userId(userId).tconst("tt0816692")
            .rating(new BigDecimal("4.5")).ratedAt(Instant.now()).build());
        assertThat(userMovieRatingRepository.findById(UserMovieId.of(userId, "tt0816692")))
            .map(UserMovieRating::getRating).hasValue(new BigDecimal("4.5"));
        assertThatThrownBy(() -> userMovieRatingRepository.saveAndFlush(UserMovieRating.builder().userId(userId)
            .tconst("tt0081505").rating(new BigDecimal("4.3")).ratedAt(Instant.now()).build()))
            .isInstanceOf(DataIntegrityViolationException.class);

        UserOnboarding onboarding = UserOnboarding.emptyFor(userId);
        onboarding.setGenres(List.of("Drama", "Sci-Fi"));
        onboarding.setCurrentStep(OnboardingStep.FAVORITE_MOVIES);
        userOnboardingRepository.save(onboarding);
        assertThat(userOnboardingRepository.findById(userId)).hasValueSatisfying(o -> {
            assertThat(o.getGenres()).containsExactly("Drama", "Sci-Fi");
            assertThat(o.getCurrentStep()).isEqualTo(OnboardingStep.FAVORITE_MOVIES);
        });

        Notification saved = notificationRepository.save(Notification.builder().userId(userId)
            .type(NotificationType.ONBOARDING).title("Bem-vindo").message("Onboarding concluído").build());
        assertThat(saved.getPublicId()).isEqualTo("n_" + saved.getId());
        assertThat(Notification.parsePublicId(saved.getPublicId())).hasValue(saved.getId());

        int first = transactionTemplate.execute(s -> notificationRepository.insertIfAbsent(userId,
            NotificationType.DAILY_RECOMMENDATION.getValue(), "Filme do dia", "Interestelar", "daily:2026-10-24"));
        int second = transactionTemplate.execute(s -> notificationRepository.insertIfAbsent(userId,
            NotificationType.DAILY_RECOMMENDATION.getValue(), "Filme do dia", "Interestelar", "daily:2026-10-24"));
        assertThat(first).isEqualTo(1);
        assertThat(second).isZero();
        assertThat(notificationRepository.countByUserIdAndReadFalse(userId)).isEqualTo(2);

        int updated = transactionTemplate.execute(s -> notificationRepository.markAllRead(userId, Instant.now()));
        assertThat(updated).isEqualTo(2);
        assertThat(notificationRepository.countByUserIdAndReadFalse(userId)).isZero();
        assertThat(notificationRepository.findByUserIdOrderByCreatedAtDescIdDesc(userId, PageRequest.of(0, 10)))
            .hasSize(2).allSatisfy(n -> assertThat(n.isRead()).isTrue());
    }
}
