package com.filmae.controller;

import com.filmae.support.AbstractIntegrationTest;
import com.filmae.support.CatalogFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("RatingController (integração)")
class RatingControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String BASE = "/api/v1/users/me/ratings";

    private String auth;

    @BeforeEach
    void setUp() {
        cleanUserData();
        cleanCatalog();
        new CatalogFixtures(jdbcTemplate).movie("tt0816692")
            .title("Interstellar").titlePt("Interestelar")
            .genres("Adventure", "Drama", "Sci-Fi").insert();
        auth = bearer(jwt.accessToken(UUID.randomUUID(), "ana@example.com", "Ana"));
    }

    @Test
    @DisplayName("PUT/GET/DELETE devolvem o formato do front (movie+summary+updatedAt / {rating})")
    void saveGetDelete() throws Exception {
        mockMvc.perform(get(BASE + "/tt0816692").header(HttpHeaders.AUTHORIZATION, auth))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.rating").value(nullValue()))
            .andExpect(jsonPath("$.meta.requestId", startsWith("req_")));

        MvcResult saved = mockMvc.perform(put(BASE + "/tt0816692").header(HttpHeaders.AUTHORIZATION, auth)
                .contentType(MediaType.APPLICATION_JSON).content("{\"rating\":4.5}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.movie.id", equalTo("tt0816692")))
            .andExpect(jsonPath("$.data.movie.userState.rating", equalTo(4.5)))
            .andExpect(jsonPath("$.data.summary.rated", equalTo(1)))
            .andExpect(jsonPath("$.data.updatedAt").isString())
            .andReturn();
        JsonContract.assertCollectionMovie(JsonContract.body(objectMapper, saved).get("data"));

        mockMvc.perform(get(BASE + "/tt0816692").header(HttpHeaders.AUTHORIZATION, auth))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.rating", equalTo(4.5)));

        mockMvc.perform(put(BASE + "/tt0816692").header(HttpHeaders.AUTHORIZATION, auth)
                .contentType(MediaType.APPLICATION_JSON).content("{\"rating\":3.0}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.movie.userState.rating", equalTo(3.0)))
            .andExpect(jsonPath("$.data.summary.rated", equalTo(1)));

        mockMvc.perform(delete(BASE + "/tt0816692").header(HttpHeaders.AUTHORIZATION, auth))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.movie.userState.rating").value(nullValue()))
            .andExpect(jsonPath("$.data.summary.rated", equalTo(0)));

        mockMvc.perform(get(BASE + "/tt0816692").header(HttpHeaders.AUTHORIZATION, auth))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.rating").value(nullValue()));

        mockMvc.perform(delete(BASE + "/tt0816692").header(HttpHeaders.AUTHORIZATION, auth))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.summary.rated", equalTo(0)));
    }

    @Test
    @DisplayName("Nota inválida ou ausente responde 400 VALIDATION_ERROR no campo rating")
    void invalidRating() throws Exception {
        mockMvc.perform(put(BASE + "/tt0816692").header(HttpHeaders.AUTHORIZATION, auth)
                .contentType(MediaType.APPLICATION_JSON).content("{\"rating\":3.3}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code", equalTo("VALIDATION_ERROR")))
            .andExpect(jsonPath("$.error.fields[0].field", equalTo("rating")))
            .andExpect(jsonPath("$.error.requestId", startsWith("req_")));

        mockMvc.perform(put(BASE + "/tt0816692").header(HttpHeaders.AUTHORIZATION, auth)
                .contentType(MediaType.APPLICATION_JSON).content("{\"rating\":0}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.fields[0].field", equalTo("rating")));

        mockMvc.perform(put(BASE + "/tt0816692").header(HttpHeaders.AUTHORIZATION, auth)
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code", equalTo("VALIDATION_ERROR")))
            .andExpect(jsonPath("$.error.fields[0].field", equalTo("rating")));
    }

    @Test
    @DisplayName("Filme inexistente → 404 RESOURCE_NOT_FOUND")
    void movieNotFound() throws Exception {
        mockMvc.perform(put(BASE + "/tt0000001").header(HttpHeaders.AUTHORIZATION, auth)
                .contentType(MediaType.APPLICATION_JSON).content("{\"rating\":4}"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code", equalTo("RESOURCE_NOT_FOUND")));
    }

    @Test
    @DisplayName("Sem token responde 401 no envelope")
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get(BASE + "/tt0816692"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code", equalTo("UNAUTHORIZED")));
    }
}
