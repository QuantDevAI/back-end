package com.filmae.service;

import com.filmae.dto.collection.CollectionSummaryResponse;
import com.filmae.dto.home.HomeResponse;
import com.filmae.dto.home.HomeResponse.HomeCollectionSummary;
import com.filmae.dto.home.HomeResponse.HomeDailyMovie;
import com.filmae.dto.home.HomeResponse.HomeUser;
import com.filmae.dto.movie.MovieListItemResponse;
import com.filmae.entity.Movie;
import com.filmae.entity.NotificationType;
import com.filmae.entity.User;
import com.filmae.repository.MovieRatingRepository;
import com.filmae.repository.NotificationRepository;
import com.filmae.service.collection.CollectionSummaryService;
import com.filmae.service.movie.MovieAssembler;
import com.filmae.service.recommendation.RecommendationService;
import com.filmae.service.recommendation.TasteProfile;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * GET /home: agrega usuário, contagens da coleção, filme do dia, recomendações, fora da bolha e
 * notificações não lidas. Na primeira chamada do dia cria a notificação {@code daily_recommendation}.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class HomeService {

    static final int RECOMMENDATIONS_LIMIT = 10;
    static final int OUTSIDE_BUBBLE_LIMIT = 10;
    /** O "dia" do filme do dia segue o horário de Brasília. */
    static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    static final String DAILY_NOTIFICATION_TITLE = "Seu filme do dia chegou";

    private final RecommendationService recommendationService;
    private final MovieAssembler movieAssembler;
    private final MovieRatingRepository movieRatingRepository;
    private final NotificationRepository notificationRepository;
    private final CollectionSummaryService collectionSummaryService;
    private final ObjectProvider<Clock> clockProvider;

    public HomeResponse home(User user) {
        Long userId = user.getId();
        LocalDate today = LocalDate.ofInstant(clockProvider.getIfAvailable(Clock::systemUTC).instant(), ZONE);
        TasteProfile profile = recommendationService.profileFor(userId);

        Optional<Movie> daily = recommendationService.dailyPick(profile, today);
        Set<String> used = new HashSet<>();
        daily.ifPresent(movie -> used.add(movie.getTconst()));

        List<Movie> recommendations = take(recommendationService.recommend(profile, RECOMMENDATIONS_LIMIT + used.size()),
            used, RECOMMENDATIONS_LIMIT);
        List<Movie> outsideBubble = take(recommendationService.outsideBubble(profile, OUTSIDE_BUBBLE_LIMIT + used.size()),
            used, OUTSIDE_BUBBLE_LIMIT);

        List<Movie> all = new ArrayList<>();
        daily.ifPresent(all::add);
        all.addAll(recommendations);
        all.addAll(outsideBubble);
        List<MovieListItemResponse> items = movieAssembler.toListItems(userId, all, profile);

        int offset = daily.isPresent() ? 1 : 0;
        HomeDailyMovie dailyMovie = daily.map(movie -> {
            MovieListItemResponse item = items.get(0);
            String reason = recommendationService
                .reasons(profile, movie, movieRatingRepository.findById(movie.getTconst()).orElse(null))
                .get(0);
            createDailyNotification(userId, today, item.getTitle());
            return new HomeDailyMovie(item, reason);
        }).orElse(null);

        CollectionSummaryResponse summary = collectionSummaryService.summary(userId);
        return HomeResponse.builder()
            .user(new HomeUser(userId, user.getName(), user.isOnboardingCompleted()))
            .collectionSummary(new HomeCollectionSummary(
                summary.getWatchlist(), summary.getWatched(), summary.getFavorite(), summary.getRewatch()))
            .dailyMovie(dailyMovie)
            .recommendations(items.subList(offset, offset + recommendations.size()))
            .outsideBubble(items.subList(offset + recommendations.size(), items.size()))
            .unreadNotifications(notificationRepository.countByUserIdAndReadFalse(userId))
            .build();
    }

    static String dailyDedupeKey(LocalDate date) {
        return NotificationType.DAILY_RECOMMENDATION.getValue() + ":" + date;
    }

    private void createDailyNotification(Long userId, LocalDate today, String title) {
        notificationRepository.insertIfAbsent(userId,
            NotificationType.DAILY_RECOMMENDATION.getValue(),
            DAILY_NOTIFICATION_TITLE,
            title + " combina com a sessão de hoje.",
            dailyDedupeKey(today));
    }

    /** Até {@code limit} filmes que ainda não apareceram em outra seção (e marca como usados). */
    private static List<Movie> take(List<Movie> movies, Set<String> used, int limit) {
        List<Movie> result = new ArrayList<>();
        for (Movie movie : movies) {
            if (result.size() >= limit) {
                break;
            }
            if (used.add(movie.getTconst())) {
                result.add(movie);
            }
        }
        return result;
    }
}
