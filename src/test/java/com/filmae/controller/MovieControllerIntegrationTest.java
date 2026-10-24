package com.filmae.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.filmae.support.AbstractIntegrationTest;
import com.filmae.support.CatalogFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("MovieController (integração)")
class MovieControllerIntegrationTest extends AbstractIntegrationTest {

    private String auth;

    @BeforeEach
    void setUp() {
        cleanUserData();
        cleanCatalog();
        CatalogFixtures catalog = new CatalogFixtures(jdbcTemplate);
        catalog.movie("tt0816692").title("Interstellar").titlePt("Interestelar")
            .year(2014).runtime(169).genres("Adventure", "Drama", "Sci-Fi")
            .rating("8.7", 2_100_000)
            .director("nm0000186", "Christopher Nolan")
            .cast("Matthew McConaughey", "Anne Hathaway")
            .insert();
        catalog.movie("tt0133093").title("The Matrix").titlePt("Matrix")
            .year(1999).runtime(136).genres("Action", "Sci-Fi")
            .rating("8.7", 2_000_000)
            .director("nm0905152", "Lana Wachowski")
            .cast("Keanu Reeves")
            .insert();
        catalog.movie("tt0903747").title("Breaking Bad").titleType("tvSeries")
            .year(2008).runtime(49).genres("Crime", "Drama", "Thriller")
            .rating("9.5", 2_200_000)
            .insert();
        auth = bearer(jwt.accessToken(UUID.randomUUID(), "ana@example.com", "Ana"));
    }

    @Test
    @DisplayName("GET /genres devolve id IMDb, name em português e movieCount")
    void genres() throws Exception {
        mockMvc.perform(get("/api/v1/genres").header(HttpHeaders.AUTHORIZATION, auth))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[?(@.id == 'Action')].name", contains("Ação")))
            .andExpect(jsonPath("$.data[?(@.id == 'Action')].movieCount", contains(1)))
            .andExpect(jsonPath("$.data[?(@.id == 'Sci-Fi')].name", contains("Ficção Científica")))
            .andExpect(jsonPath("$.data[?(@.id == 'Sci-Fi')].movieCount", contains(2)))
            .andExpect(jsonPath("$.data[?(@.id == 'Drama')].movieCount", contains(2)))
            .andExpect(jsonPath("$.data[?(@.id == 'Western')]", empty()))
            .andExpect(jsonPath("$.meta.requestId", startsWith("req_")));
    }

    @Test
    @DisplayName("GET /movies usa envelope duplo data.data + data.meta no formato do front")
    void searchEnvelope() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/movies").header(HttpHeaders.AUTHORIZATION, auth)
                .param("size", "2"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.data", hasSize(2)))
            .andExpect(jsonPath("$.data.meta", aMapWithSize(4)))
            .andExpect(jsonPath("$.data.meta.page", equalTo(0)))
            .andExpect(jsonPath("$.data.meta.size", equalTo(2)))
            .andExpect(jsonPath("$.data.meta.totalElements", equalTo(3)))
            .andExpect(jsonPath("$.data.meta.totalPages", equalTo(2)))
            .andExpect(jsonPath("$.meta.requestId", startsWith("req_")))
            .andReturn();

        JsonNode body = JsonContract.body(objectMapper, result);
        JsonContract.assertKeys(body.get("data").get("meta"), JsonContract.PAGE_META);
        body.get("data").get("data").forEach(JsonContract::assertListItem);
    }

    @Test
    @DisplayName("Busca por q encontra título BR; genres[] e CSV aceitam slug ou nome IMDb")
    void searchFilters() throws Exception {
        mockMvc.perform(get("/api/v1/movies").header(HttpHeaders.AUTHORIZATION, auth).param("q", "Interestelar"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.data", hasSize(1)))
            .andExpect(jsonPath("$.data.data[0].id", equalTo("tt0816692")))
            .andExpect(jsonPath("$.data.data[0].title", equalTo("Interestelar")))
            .andExpect(jsonPath("$.data.data[0].director", equalTo("Christopher Nolan")))
            .andExpect(jsonPath("$.data.data[0].year", equalTo(2014)));

        mockMvc.perform(get("/api/v1/movies").header(HttpHeaders.AUTHORIZATION, auth)
                .param("genres[]", "acao").param("genres[]", "ficcao-cientifica"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.data[*].id", contains("tt0816692", "tt0133093")));

        mockMvc.perform(get("/api/v1/movies").header(HttpHeaders.AUTHORIZATION, auth)
                .param("genres", "Sci-Fi,Drama"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.data[*].id", contains("tt0903747", "tt0816692", "tt0133093")));

        mockMvc.perform(get("/api/v1/movies").header(HttpHeaders.AUTHORIZATION, auth)
                .param("type", "series").param("yearFrom", "2000"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.data", hasSize(1)))
            .andExpect(jsonPath("$.data.data[0].id", equalTo("tt0903747")));
    }

    @Test
    @DisplayName("size acima de 50 é limitado; page/size/yearFrom/minRating inválidos viram 400")
    void searchValidation() throws Exception {
        mockMvc.perform(get("/api/v1/movies").header(HttpHeaders.AUTHORIZATION, auth).param("size", "500"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.meta.size", equalTo(50)));

        mockMvc.perform(get("/api/v1/movies").header(HttpHeaders.AUTHORIZATION, auth).param("page", "-1"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code", equalTo("VALIDATION_ERROR")))
            .andExpect(jsonPath("$.error.fields[0].field", equalTo("page")));

        mockMvc.perform(get("/api/v1/movies").header(HttpHeaders.AUTHORIZATION, auth).param("size", "0"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.fields[0].field", equalTo("size")));

        mockMvc.perform(get("/api/v1/movies").header(HttpHeaders.AUTHORIZATION, auth)
                .param("yearFrom", "2010").param("yearTo", "2000"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.fields[0].field", equalTo("yearFrom")));

        mockMvc.perform(get("/api/v1/movies").header(HttpHeaders.AUTHORIZATION, auth).param("minRating", "11"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.fields[0].field", equalTo("minRating")));
    }

    @Test
    @DisplayName("GET /movies/{tconst} devolve MovieDetails com elenco, sinopse vazia e userState")
    void details() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/movies/tt0816692").header(HttpHeaders.AUTHORIZATION, auth))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id", equalTo("tt0816692")))
            .andExpect(jsonPath("$.data.title", equalTo("Interestelar")))
            .andExpect(jsonPath("$.data.originalTitle", equalTo("Interstellar")))
            .andExpect(jsonPath("$.data.type", equalTo("movie")))
            .andExpect(jsonPath("$.data.director", equalTo("Christopher Nolan")))
            .andExpect(jsonPath("$.data.cast", contains("Matthew McConaughey", "Anne Hathaway")))
            .andExpect(jsonPath("$.data.synopsis", equalTo("")))
            .andExpect(jsonPath("$.data.genres", contains("Adventure", "Drama", "Sci-Fi")))
            .andExpect(jsonPath("$.data.imdb.rating", equalTo(8.7)))
            .andExpect(jsonPath("$.data.userState.watchlist", equalTo(false)))
            .andExpect(jsonPath("$.data.userState.rating").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.data.compatibilityReasons").isArray())
            .andExpect(jsonPath("$.meta.requestId", startsWith("req_")))
            .andReturn();

        JsonNode data = JsonContract.body(objectMapper, result).get("data");
        JsonContract.assertKeys(data, JsonContract.DETAILS);
        JsonContract.assertKeys(data.get("imdb"), JsonContract.IMDB);
        JsonContract.assertKeys(data.get("userState"), JsonContract.USER_STATE);
    }

    @Test
    @DisplayName("GET /movies/{tconst} inexistente responde 404 RESOURCE_NOT_FOUND")
    void detailsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/movies/tt0000001").header(HttpHeaders.AUTHORIZATION, auth))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code", equalTo("RESOURCE_NOT_FOUND")))
            .andExpect(jsonPath("$.error.requestId", startsWith("req_")))
            .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("Catálogo vazio: /movies devolve lista vazia; /genres só os canônicos do onboarding")
    void emptyCatalog() throws Exception {
        cleanCatalog();

        mockMvc.perform(get("/api/v1/movies").header(HttpHeaders.AUTHORIZATION, auth))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.data", empty()))
            .andExpect(jsonPath("$.data.meta.totalElements", equalTo(0)));

        mockMvc.perform(get("/api/v1/genres").header(HttpHeaders.AUTHORIZATION, auth))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[?(@.id == 'Action')].movieCount", contains(0)));
    }

    @Test
    @DisplayName("Sem token responde 401 no envelope")
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/genres"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code", equalTo("UNAUTHORIZED")));
        mockMvc.perform(get("/api/v1/movies"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code", equalTo("UNAUTHORIZED")));
        mockMvc.perform(get("/api/v1/movies/tt0816692"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code", equalTo("UNAUTHORIZED")));
    }
}
