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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("OnboardingController (integração)")
class OnboardingControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String BASE = "/api/v1/users/me/onboarding";

    private final UUID authId = UUID.randomUUID();
    private String auth;

    @BeforeEach
    void setUp() {
        cleanUserData();
        cleanCatalog();
        CatalogFixtures catalog = new CatalogFixtures(jdbcTemplate);
        catalog.movie("tt0816692").title("Interstellar").titlePt("Interestelar").genres("Adventure", "Drama", "Sci-Fi").insert();
        catalog.movie("tt0133093").title("The Matrix").genres("Action", "Sci-Fi").insert();
        auth = bearer(jwt.accessToken(authId, "ana@example.com", "Ana"));
    }

    private void putJson(String path, String body, int expectedStatus) throws Exception {
        mockMvc.perform(put(BASE + path).header(HttpHeaders.AUTHORIZATION, auth)
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().is(expectedStatus));
    }

    @Test
    @DisplayName("GET sem progresso devolve o estado inicial no formato do front")
    void initialState() throws Exception {
        mockMvc.perform(get(BASE).header(HttpHeaders.AUTHORIZATION, auth))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data", aMapWithSize(5)))
            .andExpect(jsonPath("$.data.completed", equalTo(false)))
            .andExpect(jsonPath("$.data.currentStep", equalTo("genres")))
            .andExpect(jsonPath("$.data.genres", empty()))
            .andExpect(jsonPath("$.data.favoriteMovies", empty()))
            .andExpect(content().json("{\"data\":{\"profileStory\":null}}"))
            .andExpect(jsonPath("$.meta.requestId", startsWith("req_")));
    }

    @Test
    @DisplayName("Fluxo completo: genres → favoriteMovies → profileStory → complete")
    void fullFlow() throws Exception {
        mockMvc.perform(put(BASE + "/genres").header(HttpHeaders.AUTHORIZATION, auth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"genres\":[\"drama\",\"ficcao-cientifica\",\"suspense\",\"Thriller\"]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data", aMapWithSize(2)))
            .andExpect(jsonPath("$.data.genres", contains("Drama", "Sci-Fi", "Thriller")))
            .andExpect(jsonPath("$.data.nextStep", equalTo("favoriteMovies")));

        mockMvc.perform(put(BASE + "/favorite-movies").header(HttpHeaders.AUTHORIZATION, auth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"movieIds\":[\"tt0816692\",\"tt0133093\"]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data", aMapWithSize(2)))
            .andExpect(jsonPath("$.data.favoriteMovies[0].id", equalTo("tt0816692")))
            .andExpect(jsonPath("$.data.favoriteMovies[0].title", equalTo("Interestelar")))
            .andExpect(jsonPath("$.data.favoriteMovies[0]", aMapWithSize(2)))
            .andExpect(jsonPath("$.data.favoriteMovies[1].title", equalTo("The Matrix")))
            .andExpect(jsonPath("$.data.nextStep", equalTo("profileStory")));

        mockMvc.perform(put(BASE + "/profile-story").header(HttpHeaders.AUTHORIZATION, auth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"  Gosto de histórias que deixam perguntas.  \"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data", aMapWithSize(2)))
            .andExpect(jsonPath("$.data.profileStory", equalTo("Gosto de histórias que deixam perguntas.")))
            .andExpect(jsonPath("$.data.nextStep", equalTo("complete")));

        mockMvc.perform(get(BASE).header(HttpHeaders.AUTHORIZATION, auth))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.completed", equalTo(false)))
            .andExpect(jsonPath("$.data.currentStep", equalTo("profileStory")))
            .andExpect(jsonPath("$.data.genres", contains("Drama", "Sci-Fi", "Thriller")))
            .andExpect(jsonPath("$.data.favoriteMovies[0].title", equalTo("Interestelar")))
            .andExpect(jsonPath("$.data.profileStory", equalTo("Gosto de histórias que deixam perguntas.")));

        MvcResult completed = mockMvc.perform(post(BASE + "/complete").header(HttpHeaders.AUTHORIZATION, auth))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data", aMapWithSize(3)))
            .andExpect(jsonPath("$.data.completed", equalTo(true)))
            .andExpect(jsonPath("$.data.completedAt").isString())
            .andExpect(jsonPath("$.data.nextScreen", equalTo("home")))
            .andExpect(jsonPath("$.meta.requestId", startsWith("req_")))
            .andReturn();
        String completedAt = objectMapper.readTree(completed.getResponse().getContentAsString()).at("/data/completedAt").asText();
        assertThat(completedAt).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z");

        assertThat(jdbcTemplate.queryForObject(
            "SELECT onboarding_completed_at IS NOT NULL FROM users WHERE auth_user_id = ?", Boolean.class, authId)).isTrue();
        assertThat(jdbcTemplate.queryForObject(
            "SELECT count(*) FROM notifications n JOIN users u ON u.id = n.user_id WHERE u.auth_user_id = ? AND n.type = 'onboarding'",
            Integer.class, authId)).isEqualTo(1);

        mockMvc.perform(post(BASE + "/complete").header(HttpHeaders.AUTHORIZATION, auth))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.completedAt", equalTo(completedAt)));
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM notifications", Integer.class)).isEqualTo(1);

        mockMvc.perform(get(BASE).header(HttpHeaders.AUTHORIZATION, auth))
            .andExpect(jsonPath("$.data.completed", equalTo(true)));
    }

    @Test
    @DisplayName("Etapas puladas pelo front (favoritos vazios e texto vazio) ainda permitem concluir")
    void skippedStepsCanComplete() throws Exception {
        putJson("/genres", "{\"genres\":[\"acao\",\"drama\",\"terror\"]}", 200);
        putJson("/favorite-movies", "{\"movieIds\":[]}", 200);
        putJson("/profile-story", "{\"text\":\"\"}", 200);

        mockMvc.perform(post(BASE + "/complete").header(HttpHeaders.AUTHORIZATION, auth))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.completed", equalTo(true)));
    }

    @Test
    @DisplayName("complete com etapas faltando responde 422 ONBOARDING_INCOMPLETE")
    void completeIncomplete() throws Exception {
        mockMvc.perform(post(BASE + "/complete").header(HttpHeaders.AUTHORIZATION, auth))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.error.code", equalTo("ONBOARDING_INCOMPLETE")))
            .andExpect(jsonPath("$.error.fields[*].field", contains("genres", "favoriteMovies", "profileStory")))
            .andExpect(jsonPath("$.error.requestId", startsWith("req_")));

        putJson("/genres", "{\"genres\":[\"drama\"]}", 200);

        mockMvc.perform(post(BASE + "/complete").header(HttpHeaders.AUTHORIZATION, auth))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.error.fields[*].field", contains("favoriteMovies", "profileStory")));
    }

    @Test
    @DisplayName("PUT fora de ordem responde 422 ONBOARDING_INCOMPLETE")
    void outOfOrder() throws Exception {
        mockMvc.perform(put(BASE + "/favorite-movies").header(HttpHeaders.AUTHORIZATION, auth)
                .contentType(MediaType.APPLICATION_JSON).content("{\"movieIds\":[\"tt0816692\"]}"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.error.code", equalTo("ONBOARDING_INCOMPLETE")));

        putJson("/genres", "{\"genres\":[\"drama\"]}", 200);

        mockMvc.perform(put(BASE + "/profile-story").header(HttpHeaders.AUTHORIZATION, auth)
                .contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"oi\"}"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.error.code", equalTo("ONBOARDING_INCOMPLETE")));
    }

    @Test
    @DisplayName("Validações: gênero inválido, tconst inexistente, texto longo e corpo ausente")
    void validations() throws Exception {
        mockMvc.perform(put(BASE + "/genres").header(HttpHeaders.AUTHORIZATION, auth)
                .contentType(MediaType.APPLICATION_JSON).content("{\"genres\":[\"drama\",\"novela\"]}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code", equalTo("VALIDATION_ERROR")))
            .andExpect(jsonPath("$.error.fields[0].field", equalTo("genres[1]")));

        mockMvc.perform(put(BASE + "/genres").header(HttpHeaders.AUTHORIZATION, auth)
                .contentType(MediaType.APPLICATION_JSON).content("{\"genres\":[]}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.fields[0].field", equalTo("genres")));

        putJson("/genres", "{\"genres\":[\"drama\"]}", 200);

        mockMvc.perform(put(BASE + "/favorite-movies").header(HttpHeaders.AUTHORIZATION, auth)
                .contentType(MediaType.APPLICATION_JSON).content("{\"movieIds\":[\"tt0816692\",\"tt0000001\"]}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code", equalTo("VALIDATION_ERROR")))
            .andExpect(jsonPath("$.error.fields[0].field", equalTo("movieIds[1]")));

        putJson("/favorite-movies", "{\"movieIds\":[\"tt0816692\"]}", 200);

        mockMvc.perform(put(BASE + "/profile-story").header(HttpHeaders.AUTHORIZATION, auth)
                .contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"" + "a".repeat(401) + "\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.fields[0].field", equalTo("text")));

        mockMvc.perform(put(BASE + "/profile-story").header(HttpHeaders.AUTHORIZATION, auth)
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code", equalTo("VALIDATION_ERROR")));
    }

    @Test
    @DisplayName("Sem token responde 401 no envelope")
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get(BASE))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code", equalTo("UNAUTHORIZED")));
    }
}
