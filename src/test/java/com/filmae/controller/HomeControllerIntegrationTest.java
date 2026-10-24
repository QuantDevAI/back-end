package com.filmae.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.filmae.support.AbstractIntegrationTest;
import com.filmae.support.CatalogFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("HomeController (integração)")
class HomeControllerIntegrationTest extends AbstractIntegrationTest {

    private static final Set<String> HOME_KEYS = Set.of(
        "user", "collectionSummary", "dailyMovie", "recommendations", "outsideBubble", "unreadNotifications");
    private static final Set<String> HOME_USER = Set.of("id", "name", "onboardingCompleted");
    private static final Set<String> HOME_SUMMARY = Set.of("watchlist", "watched", "favorite", "rewatch");
    private static final Set<String> DAILY = union(JsonContract.LIST_ITEM, Set.of("reason"));

    private String auth;
    private final UUID authId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        cleanUserData();
        cleanCatalog();
        CatalogFixtures catalog = new CatalogFixtures(jdbcTemplate);
        catalog.movie("tt0816692").title("Interstellar").titlePt("Interestelar")
            .year(2014).genres("Adventure", "Drama", "Sci-Fi").rating("8.7", 2_100_000).insert();
        catalog.movie("tt0133093").title("The Matrix").titlePt("Matrix")
            .year(1999).genres("Action", "Sci-Fi").rating("8.7", 2_000_000).insert();
        catalog.movie("tt0468569").title("The Dark Knight")
            .year(2008).genres("Action", "Crime", "Drama").rating("9.0", 2_900_000).insert();
        catalog.movie("tt0111161").title("The Shawshank Redemption")
            .year(1994).genres("Drama").rating("9.3", 2_900_000).insert();
        catalog.movie("tt0167260").title("The Lord of the Rings: The Return of the King")
            .year(2003).genres("Adventure", "Drama", "Fantasy").rating("9.0", 2_000_000).insert();
        catalog.movie("tt1375666").title("Inception")
            .year(2010).genres("Action", "Adventure", "Sci-Fi").rating("8.8", 2_600_000).insert();
        catalog.movie("tt0109830").title("Forrest Gump")
            .year(1994).genres("Drama", "Romance").rating("8.8", 2_300_000).insert();
        catalog.movie("tt0137523").title("Fight Club")
            .year(1999).genres("Drama").rating("8.8", 2_400_000).insert();
        catalog.movie("tt0110912").title("Pulp Fiction")
            .year(1994).genres("Crime", "Drama").rating("8.9", 2_200_000).insert();
        catalog.movie("tt0120737").title("The Lord of the Rings: The Fellowship of the Ring")
            .year(2001).genres("Adventure", "Drama", "Fantasy").rating("8.9", 2_000_000).insert();
        catalog.movie("tt0102926").title("The Silence of the Lambs")
            .year(1991).genres("Crime", "Drama", "Thriller").rating("8.6", 1_600_000).insert();
        catalog.movie("tt0114369").title("Se7en")
            .year(1995).genres("Crime", "Drama", "Mystery").rating("8.6", 1_800_000).insert();
        auth = bearer(jwt.accessToken(authId, "ana@example.com", "Ana"));
    }

    @Test
    @DisplayName("GET /home monta o payload do front, cria a notificação diária e não duplica")
    void homePayloadAndDailyNotification() throws Exception {
        JsonNode first = JsonContract.body(objectMapper, mockMvc.perform(get("/api/v1/home")
                .header(HttpHeaders.AUTHORIZATION, auth))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.user.name", equalTo("Ana")))
            .andExpect(jsonPath("$.data.user.onboardingCompleted", equalTo(false)))
            .andExpect(jsonPath("$.data.user.id").isNumber())
            .andExpect(jsonPath("$.data.collectionSummary.watchlist", equalTo(0)))
            .andExpect(jsonPath("$.data.collectionSummary.watched", equalTo(0)))
            .andExpect(jsonPath("$.data.unreadNotifications", equalTo(1)))
            .andExpect(jsonPath("$.data.dailyMovie.reason").isString())
            .andExpect(jsonPath("$.data.recommendations").isArray())
            .andExpect(jsonPath("$.data.outsideBubble").isArray())
            .andExpect(jsonPath("$.meta.requestId", startsWith("req_")))
            .andReturn());

        JsonNode data = first.get("data");
        JsonContract.assertKeys(data, HOME_KEYS);
        JsonContract.assertKeys(data.get("user"), HOME_USER);
        JsonContract.assertKeys(data.get("collectionSummary"), HOME_SUMMARY);
        JsonContract.assertKeys(data.get("dailyMovie"), DAILY);
        JsonContract.assertKeys(data.get("dailyMovie").get("imdb"), JsonContract.IMDB);
        JsonContract.assertKeys(data.get("dailyMovie").get("userState"), JsonContract.USER_STATE);
        data.get("recommendations").forEach(JsonContract::assertListItem);
        data.get("outsideBubble").forEach(JsonContract::assertListItem);

        Set<String> ids = new TreeSet<>();
        ids.add(data.get("dailyMovie").get("id").asText());
        data.get("recommendations").forEach(n -> ids.add(n.get("id").asText()));
        data.get("outsideBubble").forEach(n -> ids.add(n.get("id").asText()));
        int listed = 1 + data.get("recommendations").size() + data.get("outsideBubble").size();
        assertThat(ids).hasSize(listed);

        assertThat(jdbcTemplate.queryForObject(
            "SELECT count(*) FROM notifications n JOIN users u ON u.id = n.user_id WHERE u.auth_user_id = ? AND n.type = 'daily_recommendation'",
            Integer.class, authId)).isEqualTo(1);

        mockMvc.perform(get("/api/v1/home").header(HttpHeaders.AUTHORIZATION, auth))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.unreadNotifications", equalTo(1)))
            .andExpect(jsonPath("$.data.dailyMovie.id", equalTo(data.get("dailyMovie").get("id").asText())));
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM notifications", Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("Coleção do usuário entra no collectionSummary da home")
    void collectionSummary() throws Exception {
        mockMvc.perform(put("/api/v1/users/me/collection/tt0816692")
                .header(HttpHeaders.AUTHORIZATION, auth)
                .contentType("application/json")
                .content("{\"watchlist\":true,\"watched\":true}"))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/home").header(HttpHeaders.AUTHORIZATION, auth))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.collectionSummary", aMapWithSize(4)))
            .andExpect(jsonPath("$.data.collectionSummary.watchlist", equalTo(1)))
            .andExpect(jsonPath("$.data.collectionSummary.watched", equalTo(1)))
            .andExpect(jsonPath("$.data.collectionSummary.favorite", equalTo(0)))
            .andExpect(jsonPath("$.data.collectionSummary.rewatch", equalTo(0)));
    }

    @Test
    @DisplayName("Catálogo vazio: dailyMovie null, listas vazias e sem notificação")
    void emptyCatalog() throws Exception {
        cleanCatalog();

        mockMvc.perform(get("/api/v1/home").header(HttpHeaders.AUTHORIZATION, auth))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.dailyMovie").value(nullValue()))
            .andExpect(jsonPath("$.data.recommendations", empty()))
            .andExpect(jsonPath("$.data.outsideBubble", empty()))
            .andExpect(jsonPath("$.data.unreadNotifications", equalTo(0)));

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM notifications", Integer.class)).isZero();
    }

    @Test
    @DisplayName("Sem token responde 401 no envelope")
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/home"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code", equalTo("UNAUTHORIZED")));
    }

    private static Set<String> union(Set<String> a, Set<String> b) {
        Set<String> result = new TreeSet<>(a);
        result.addAll(b);
        return result;
    }
}
