package com.filmae.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.filmae.support.AbstractIntegrationTest;
import com.filmae.support.CatalogFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("CollectionController (integração)")
class CollectionControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String BASE = "/api/v1/users/me/collection";

    private String auth;

    @BeforeEach
    void setUp() {
        cleanUserData();
        cleanCatalog();
        CatalogFixtures catalog = new CatalogFixtures(jdbcTemplate);
        catalog.movie("tt0816692").title("Interstellar").titlePt("Interestelar")
            .genres("Adventure", "Drama", "Sci-Fi").insert();
        catalog.movie("tt0133093").title("The Matrix").genres("Action", "Sci-Fi").insert();
        auth = bearer(jwt.accessToken(UUID.randomUUID(), "ana@example.com", "Ana"));
    }

    @Test
    @DisplayName("GET vazio devolve items [] e summary zerado")
    void emptyCollection() throws Exception {
        mockMvc.perform(get(BASE).header(HttpHeaders.AUTHORIZATION, auth))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data", aMapWithSize(2)))
            .andExpect(jsonPath("$.data.items", empty()))
            .andExpect(jsonPath("$.data.summary.total", equalTo(0)))
            .andExpect(jsonPath("$.data.summary.watched", equalTo(0)))
            .andExpect(jsonPath("$.data.summary.watchlist", equalTo(0)))
            .andExpect(jsonPath("$.data.summary.favorite", equalTo(0)))
            .andExpect(jsonPath("$.data.summary.rewatch", equalTo(0)))
            .andExpect(jsonPath("$.data.summary.rated", equalTo(0)))
            .andExpect(jsonPath("$.meta.requestId", startsWith("req_")));
    }

    @Test
    @DisplayName("PUT parcial, GET, filtro e DELETE (zera flags e mantém a nota)")
    void upsertGetFilterAndDelete() throws Exception {
        MvcResult created = mockMvc.perform(put(BASE + "/tt0816692").header(HttpHeaders.AUTHORIZATION, auth)
                .contentType(MediaType.APPLICATION_JSON).content("{\"watchlist\":true}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.movie.id", equalTo("tt0816692")))
            .andExpect(jsonPath("$.data.movie.userState.watchlist", equalTo(true)))
            .andExpect(jsonPath("$.data.movie.userState.watched", equalTo(false)))
            .andExpect(jsonPath("$.data.movie.userState.favorite", equalTo(false)))
            .andExpect(jsonPath("$.data.summary.watchlist", equalTo(1)))
            .andExpect(jsonPath("$.data.summary.total", equalTo(1)))
            .andReturn();
        JsonContract.assertCollectionMovie(JsonContract.body(objectMapper, created).get("data"));

        mockMvc.perform(put(BASE + "/tt0816692").header(HttpHeaders.AUTHORIZATION, auth)
                .contentType(MediaType.APPLICATION_JSON).content("{\"watched\":true,\"favorite\":true}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.movie.userState.watchlist", equalTo(true)))
            .andExpect(jsonPath("$.data.movie.userState.watched", equalTo(true)))
            .andExpect(jsonPath("$.data.movie.userState.favorite", equalTo(true)))
            .andExpect(jsonPath("$.data.movie.userState.rewatch", equalTo(false)))
            .andExpect(jsonPath("$.data.summary.watched", equalTo(1)))
            .andExpect(jsonPath("$.data.summary.favorite", equalTo(1)));

        mockMvc.perform(put(BASE + "/tt0133093").header(HttpHeaders.AUTHORIZATION, auth)
                .contentType(MediaType.APPLICATION_JSON).content("{\"rewatch\":true}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.summary.rewatch", equalTo(1)))
            .andExpect(jsonPath("$.data.summary.total", equalTo(2)));

        mockMvc.perform(get(BASE).header(HttpHeaders.AUTHORIZATION, auth))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items", hasSize(2)))
            .andExpect(jsonPath("$.data.summary.total", equalTo(2)));

        mockMvc.perform(get(BASE).header(HttpHeaders.AUTHORIZATION, auth).param("filter", "favorite"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items", hasSize(1)))
            .andExpect(jsonPath("$.data.items[0].id", equalTo("tt0816692")))
            .andExpect(jsonPath("$.data.summary.favorite", equalTo(1)));

        mockMvc.perform(get(BASE + "/tt0816692").header(HttpHeaders.AUTHORIZATION, auth))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.movie.userState.watched", equalTo(true)))
            .andExpect(jsonPath("$.data.updatedAt").isString());

        mockMvc.perform(put("/api/v1/users/me/ratings/tt0816692").header(HttpHeaders.AUTHORIZATION, auth)
                .contentType(MediaType.APPLICATION_JSON).content("{\"rating\":4.5}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.summary.rated", equalTo(1)));

        mockMvc.perform(delete(BASE + "/tt0816692").header(HttpHeaders.AUTHORIZATION, auth))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.movie.userState.watchlist", equalTo(false)))
            .andExpect(jsonPath("$.data.movie.userState.watched", equalTo(false)))
            .andExpect(jsonPath("$.data.movie.userState.favorite", equalTo(false)))
            .andExpect(jsonPath("$.data.movie.userState.rewatch", equalTo(false)))
            .andExpect(jsonPath("$.data.movie.userState.rating", equalTo(4.5)))
            .andExpect(jsonPath("$.data.summary.total", equalTo(1)))
            .andExpect(jsonPath("$.data.summary.rated", equalTo(1)));

        JsonNode rating = JsonContract.body(objectMapper, mockMvc.perform(
                get("/api/v1/users/me/ratings/tt0816692").header(HttpHeaders.AUTHORIZATION, auth))
            .andExpect(status().isOk()).andReturn()).get("data");
        assertThat(rating.get("rating").asDouble()).isEqualTo(4.5);
    }

    @Test
    @DisplayName("Filme fora da coleção: GET devolve userState vazio; DELETE não quebra")
    void movieNotInCollection() throws Exception {
        mockMvc.perform(get(BASE + "/tt0816692").header(HttpHeaders.AUTHORIZATION, auth))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.movie.userState.watchlist", equalTo(false)))
            .andExpect(jsonPath("$.data.summary.total", equalTo(0)));

        mockMvc.perform(delete(BASE + "/tt0816692").header(HttpHeaders.AUTHORIZATION, auth))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.movie.id", equalTo("tt0816692")))
            .andExpect(jsonPath("$.data.summary.total", equalTo(0)));
    }

    @Test
    @DisplayName("Filme inexistente → 404; filtro inválido → 400")
    void errors() throws Exception {
        mockMvc.perform(put(BASE + "/tt0000001").header(HttpHeaders.AUTHORIZATION, auth)
                .contentType(MediaType.APPLICATION_JSON).content("{\"watchlist\":true}"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code", equalTo("RESOURCE_NOT_FOUND")));

        mockMvc.perform(get(BASE).header(HttpHeaders.AUTHORIZATION, auth).param("filter", "rated"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code", equalTo("VALIDATION_ERROR")))
            .andExpect(jsonPath("$.error.fields[0].field", equalTo("filter")));
    }

    @Test
    @DisplayName("Sem token responde 401 no envelope")
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get(BASE))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code", equalTo("UNAUTHORIZED")));
    }
}
