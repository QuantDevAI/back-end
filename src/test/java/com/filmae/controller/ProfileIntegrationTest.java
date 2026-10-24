package com.filmae.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.filmae.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("/api/v1/users/me e /preferences")
class ProfileIntegrationTest extends AbstractIntegrationTest {

    private static final String ME = "/api/v1/users/me";

    private String token;

    @BeforeEach
    void setUp() {
        cleanUserData();
        token = jwt.accessToken(UUID.randomUUID(), "pedro@example.com", "Pedro");
    }

    @Test
    @DisplayName("GET /users/me devolve {id,name,email,bio,createdAt} com bio vazia por padrão")
    void getProfile() throws Exception {
        String body = mockMvc.perform(get(ME).header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id", notNullValue()))
            .andExpect(jsonPath("$.data.name", equalTo("Pedro")))
            .andExpect(jsonPath("$.data.email", equalTo("pedro@example.com")))
            .andExpect(jsonPath("$.data.bio", equalTo("")))
            .andExpect(jsonPath("$.data.createdAt", notNullValue()))
            .andReturn().getResponse().getContentAsString();

        JsonNode data = objectMapper.readTree(body).get("data");
        assertThat(data.get("id").isIntegralNumber()).isTrue();
        assertThat(fieldNames(data)).containsExactlyInAnyOrder("id", "name", "email", "bio", "createdAt");
    }

    @Test
    @DisplayName("PATCH /users/me é parcial e ignora tentativa de trocar o e-mail")
    void patchProfilePartially() throws Exception {
        mockMvc.perform(patch(ME).header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Pedro Silva\", \"bio\": \"Prefiro filmes com atmosfera intensa.\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.name", equalTo("Pedro Silva")))
            .andExpect(jsonPath("$.data.bio", equalTo("Prefiro filmes com atmosfera intensa.")));

        mockMvc.perform(patch(ME).header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"bio\": \"Nova bio\", \"email\": \"hacker@example.com\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.name", equalTo("Pedro Silva")))
            .andExpect(jsonPath("$.data.bio", equalTo("Nova bio")))
            .andExpect(jsonPath("$.data.email", equalTo("pedro@example.com")));

        mockMvc.perform(get(ME).header("Authorization", bearer(token)))
            .andExpect(jsonPath("$.data.name", equalTo("Pedro Silva")))
            .andExpect(jsonPath("$.data.bio", equalTo("Nova bio")))
            .andExpect(jsonPath("$.data.email", equalTo("pedro@example.com")));
    }

    @Test
    @DisplayName("PATCH /users/me valida tamanho e nome em branco")
    void patchProfileValidation() throws Exception {
        mockMvc.perform(patch(ME).header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"" + "a".repeat(151) + "\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code", equalTo("VALIDATION_ERROR")))
            .andExpect(jsonPath("$.error.fields[0].field", equalTo("name")));

        mockMvc.perform(patch(ME).header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"bio\": \"" + "b".repeat(501) + "\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.fields[0].field", equalTo("bio")));

        mockMvc.perform(patch(ME).header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"   \"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.fields[0].field", equalTo("name")));

        mockMvc.perform(get(ME).header("Authorization", bearer(token)))
            .andExpect(jsonPath("$.data.name", equalTo("Pedro")));
    }

    @Test
    @DisplayName("Alterações de perfil e preferências não afetam outro usuário")
    void profileIsIsolatedPerUser() throws Exception {
        String otherToken = jwt.accessToken(UUID.randomUUID(), "ana@example.com", "Ana");

        mockMvc.perform(patch(ME).header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"bio\": \"Bio do Pedro\"}"))
            .andExpect(status().isOk());
        mockMvc.perform(patch(ME + "/preferences").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content("{\"spoilerModeEnabled\": true}"))
            .andExpect(status().isOk());

        mockMvc.perform(get(ME).header("Authorization", bearer(otherToken)))
            .andExpect(jsonPath("$.data.name", equalTo("Ana")))
            .andExpect(jsonPath("$.data.email", equalTo("ana@example.com")))
            .andExpect(jsonPath("$.data.bio", equalTo("")));
        mockMvc.perform(get(ME + "/preferences").header("Authorization", bearer(otherToken)))
            .andExpect(jsonPath("$.data.spoilerModeEnabled", equalTo(false)));
    }

    @Test
    @DisplayName("GET e PATCH /users/me/preferences com defaults e atualização parcial")
    void preferences() throws Exception {
        String body = mockMvc.perform(get(ME + "/preferences").header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.receiveRecommendationsByEmail", equalTo(true)))
            .andExpect(jsonPath("$.data.spoilerModeEnabled", equalTo(false)))
            .andReturn().getResponse().getContentAsString();
        assertThat(fieldNames(objectMapper.readTree(body).get("data")))
            .containsExactlyInAnyOrder("receiveRecommendationsByEmail", "spoilerModeEnabled");

        mockMvc.perform(patch(ME + "/preferences").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content("{\"receiveRecommendationsByEmail\": false}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.receiveRecommendationsByEmail", equalTo(false)))
            .andExpect(jsonPath("$.data.spoilerModeEnabled", equalTo(false)));

        mockMvc.perform(patch(ME + "/preferences").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content("{\"spoilerModeEnabled\": true}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.receiveRecommendationsByEmail", equalTo(false)))
            .andExpect(jsonPath("$.data.spoilerModeEnabled", equalTo(true)));
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
