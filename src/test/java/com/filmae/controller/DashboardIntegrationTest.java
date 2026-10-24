package com.filmae.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.filmae.support.AbstractIntegrationTest;
import com.filmae.support.CatalogFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("GET /api/v1/users/me/dashboard")
class DashboardIntegrationTest extends AbstractIntegrationTest {

    private CatalogFixtures catalog;

    @BeforeEach
    void setUp() {
        cleanUserData();
        cleanCatalog();
        catalog = new CatalogFixtures(jdbcTemplate);
        catalog.movie("tt0816692").title("Interstellar").titlePt("Interestelar").year(2014).runtime(169)
            .genres("Adventure", "Drama", "Sci-Fi").insert();
        catalog.movie("tt0133093").title("The Matrix").year(1999).runtime(136).genres("Action", "Sci-Fi").insert();
        catalog.movie("tt0111161").title("The Shawshank Redemption").titlePt("Um Sonho de Liberdade").year(1994)
            .runtime(142).genres("Drama").insert();
        catalog.movie("tt1375666").title("Inception").year(2010).runtime(148).genres("Action", "Sci-Fi").insert();
    }

    @Test
    @DisplayName("KPIs, topGenres e historyPreview refletem a coleção do usuário com o JSON do front")
    void dashboardWithCollection() throws Exception {
        String token = newUserToken();
        long userId = createUser(token);

        insertState(userId, "tt0816692", true, false, true, true, Instant.parse("2026-10-02T16:00:00Z"));
        insertState(userId, "tt0133093", true, false, false, false, Instant.parse("2026-09-15T10:00:00Z"));
        insertState(userId, "tt0111161", true, false, true, false, Instant.parse("2026-09-20T20:30:00Z"));
        insertState(userId, "tt1375666", false, true, false, false, null);
        insertRating(userId, "tt0816692", "4.5");
        insertRating(userId, "tt1375666", "3.0");

        String body = mockMvc.perform(get("/api/v1/users/me/dashboard").header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.meta.requestId", startsWith("req_")))
            .andExpect(jsonPath("$.data.kpis.watchedCount", equalTo(3)))
            .andExpect(jsonPath("$.data.kpis.totalMinutesWatched", equalTo(169 + 136 + 142)))
            .andExpect(jsonPath("$.data.kpis.favoriteCount", equalTo(2)))
            .andExpect(jsonPath("$.data.kpis.rewatchCount", equalTo(1)))
            .andExpect(jsonPath("$.data.kpis.ratedCount", equalTo(2)))
            .andExpect(jsonPath("$.data.kpis.watchlistCount", equalTo(1)))
            .andExpect(jsonPath("$.data.topGenres", hasSize(4)))
            .andExpect(jsonPath("$.data.topGenres[0].genre", equalTo("Drama")))
            .andExpect(jsonPath("$.data.topGenres[0].count", equalTo(2)))
            .andExpect(jsonPath("$.data.topGenres[1].genre", equalTo("Sci-Fi")))
            .andExpect(jsonPath("$.data.topGenres[1].count", equalTo(2)))
            .andExpect(jsonPath("$.data.historyPreview", hasSize(3)))
            .andExpect(jsonPath("$.data.historyPreview[0].movieId", equalTo("tt0816692")))
            .andExpect(jsonPath("$.data.historyPreview[0].title", equalTo("Interestelar")))
            .andExpect(jsonPath("$.data.historyPreview[0].year", equalTo(2014)))
            .andExpect(jsonPath("$.data.historyPreview[0].watchedAt", equalTo("2026-10-02T16:00:00Z")))
            .andExpect(jsonPath("$.data.historyPreview[0].rating", equalTo(4.5)))
            .andExpect(jsonPath("$.data.historyPreview[1].movieId", equalTo("tt0111161")))
            .andExpect(jsonPath("$.data.historyPreview[1].title", equalTo("Um Sonho de Liberdade")))
            .andExpect(jsonPath("$.data.historyPreview[2].movieId", equalTo("tt0133093")))
            .andExpect(jsonPath("$.data.historyPreview[2].title", equalTo("The Matrix")))
            .andExpect(jsonPath("$.data.historyPreview[2].rating", nullValue()))
            .andExpect(jsonPath("$.data.updatedAt", notNullValue()))
            .andReturn().getResponse().getContentAsString();

        JsonNode data = objectMapper.readTree(body).get("data");
        assertThat(fieldNames(data)).containsExactlyInAnyOrder("kpis", "topGenres", "historyPreview", "updatedAt");
        assertThat(fieldNames(data.get("kpis"))).containsExactlyInAnyOrder("watchedCount", "totalMinutesWatched",
            "favoriteCount", "rewatchCount", "ratedCount", "watchlistCount");
        assertThat(fieldNames(data.get("topGenres").get(0))).containsExactlyInAnyOrder("genre", "count");
        assertThat(fieldNames(data.get("historyPreview").get(2)))
            .containsExactlyInAnyOrder("movieId", "title", "year", "watchedAt", "rating");
    }

    @Test
    @DisplayName("Usuário sem coleção recebe KPIs zerados e listas vazias; dados de outro usuário não vazam")
    void dashboardIsIsolatedPerUser() throws Exception {
        String ownerToken = newUserToken();
        long ownerId = createUser(ownerToken);
        insertState(ownerId, "tt0816692", true, true, true, true, Instant.parse("2026-10-02T16:00:00Z"));
        insertRating(ownerId, "tt0816692", "5.0");

        String otherToken = newUserToken();
        mockMvc.perform(get("/api/v1/users/me/dashboard").header("Authorization", bearer(otherToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.kpis.watchedCount", equalTo(0)))
            .andExpect(jsonPath("$.data.kpis.totalMinutesWatched", equalTo(0)))
            .andExpect(jsonPath("$.data.kpis.favoriteCount", equalTo(0)))
            .andExpect(jsonPath("$.data.kpis.rewatchCount", equalTo(0)))
            .andExpect(jsonPath("$.data.kpis.ratedCount", equalTo(0)))
            .andExpect(jsonPath("$.data.kpis.watchlistCount", equalTo(0)))
            .andExpect(jsonPath("$.data.topGenres", hasSize(0)))
            .andExpect(jsonPath("$.data.historyPreview", hasSize(0)));
    }

    @Test
    @DisplayName("Sem token responde 401")
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/users/me/dashboard"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code", equalTo("UNAUTHORIZED")));
    }

    private long createUser(String token) throws Exception {
        String body = mockMvc.perform(get("/api/v1/users/me").header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).at("/data/id").asLong();
    }

    private void insertState(long userId, String tconst, boolean watched, boolean watchlist, boolean favorite,
                             boolean rewatch, Instant watchedAt) {
        jdbcTemplate.update("""
            INSERT INTO user_movie_states (user_id, tconst, watched, watchlist, favorite, rewatch, watched_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """, userId, tconst, watched, watchlist, favorite, rewatch,
            watchedAt != null ? Timestamp.from(watchedAt) : null);
    }

    private void insertRating(long userId, String tconst, String rating) {
        jdbcTemplate.update("INSERT INTO user_movie_ratings (user_id, tconst, rating) VALUES (?, ?, ?::numeric)",
            userId, tconst, rating);
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
