package com.filmae.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.filmae.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("/api/v1/users/me/notifications")
class NotificationIntegrationTest extends AbstractIntegrationTest {

    private static final String BASE = "/api/v1/users/me/notifications";

    private String token;
    private long userId;
    private long olderId;
    private long newerId;

    @BeforeEach
    void setUp() throws Exception {
        cleanUserData();
        token = newUserToken();
        userId = createUser(token);
        olderId = insertNotification(userId, "onboarding", "Bem-vindo", "Onboarding concluído", false,
            Instant.parse("2026-10-01T09:00:00Z"));
        newerId = insertNotification(userId, "daily_recommendation", "Seu filme do dia chegou",
            "Interestelar combina com a sessão de hoje.", false, Instant.parse("2026-10-02T12:00:00Z"));
    }

    @Test
    @DisplayName("GET lista mais recentes primeiro com o formato AppNotification e unreadCount")
    void listNotifications() throws Exception {
        String body = mockMvc.perform(get(BASE).header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.unreadCount", equalTo(2)))
            .andExpect(jsonPath("$.data.items", hasSize(2)))
            .andExpect(jsonPath("$.data.items[0].id", equalTo("n_" + newerId)))
            .andExpect(jsonPath("$.data.items[0].title", equalTo("Seu filme do dia chegou")))
            .andExpect(jsonPath("$.data.items[0].message", equalTo("Interestelar combina com a sessão de hoje.")))
            .andExpect(jsonPath("$.data.items[0].createdAt", equalTo("2026-10-02T12:00:00Z")))
            .andExpect(jsonPath("$.data.items[0].read", equalTo(false)))
            .andExpect(jsonPath("$.data.items[0].type", equalTo("daily_recommendation")))
            .andExpect(jsonPath("$.data.items[1].id", equalTo("n_" + olderId)))
            .andExpect(jsonPath("$.data.items[1].type", equalTo("onboarding")))
            .andReturn().getResponse().getContentAsString();

        JsonNode data = objectMapper.readTree(body).get("data");
        assertThat(fieldNames(data)).containsExactlyInAnyOrder("items", "unreadCount");
        assertThat(fieldNames(data.get("items").get(0)))
            .containsExactlyInAnyOrder("id", "title", "message", "createdAt", "read", "type");
    }

    @Test
    @DisplayName("PATCH sem body marca como lida e devolve o NotificationsPayload completo")
    void patchWithoutBodyMarksRead() throws Exception {
        mockMvc.perform(patch(BASE + "/n_" + newerId).header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.unreadCount", equalTo(1)))
            .andExpect(jsonPath("$.data.items", hasSize(2)))
            .andExpect(jsonPath("$.data.items[0].id", equalTo("n_" + newerId)))
            .andExpect(jsonPath("$.data.items[0].read", equalTo(true)))
            .andExpect(jsonPath("$.data.items[1].read", equalTo(false)));

        assertThat(jdbcTemplate.queryForObject("SELECT read_at IS NOT NULL FROM notifications WHERE id = ?",
            Boolean.class, newerId)).isTrue();
    }

    @Test
    @DisplayName("PATCH com {read:false} volta para não lida")
    void patchWithReadFalse() throws Exception {
        mockMvc.perform(patch(BASE + "/n_" + newerId).header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content("{\"read\": true}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.unreadCount", equalTo(1)));

        mockMvc.perform(patch(BASE + "/n_" + newerId).header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content("{\"read\": false}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.unreadCount", equalTo(2)))
            .andExpect(jsonPath("$.data.items[0].read", equalTo(false)));
    }

    @Test
    @DisplayName("PATCH com id mal formatado ou inexistente responde 404")
    void patchInvalidIdIsNotFound() throws Exception {
        for (String id : List.of("31", "n_abc", "n_-5", "n_999999")) {
            mockMvc.perform(patch(BASE + "/" + id).header("Authorization", bearer(token)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code", equalTo("RESOURCE_NOT_FOUND")));
        }
    }

    @Test
    @DisplayName("Outro usuário não vê nem altera as notificações do dono (404)")
    void notificationsAreIsolatedPerUser() throws Exception {
        String otherToken = newUserToken();

        mockMvc.perform(get(BASE).header("Authorization", bearer(otherToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items", hasSize(0)))
            .andExpect(jsonPath("$.data.unreadCount", equalTo(0)));

        mockMvc.perform(patch(BASE + "/n_" + newerId).header("Authorization", bearer(otherToken)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code", equalTo("RESOURCE_NOT_FOUND")));

        mockMvc.perform(post(BASE + "/read-all").header("Authorization", bearer(otherToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.unreadCount", equalTo(0)));

        mockMvc.perform(get(BASE).header("Authorization", bearer(token)))
            .andExpect(jsonPath("$.data.unreadCount", equalTo(2)));
    }

    @Test
    @DisplayName("POST read-all marca todas e devolve o NotificationsPayload completo")
    void readAll() throws Exception {
        String body = mockMvc.perform(post(BASE + "/read-all").header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.unreadCount", equalTo(0)))
            .andExpect(jsonPath("$.data.items", hasSize(2)))
            .andExpect(jsonPath("$.data.items[0].read", equalTo(true)))
            .andExpect(jsonPath("$.data.items[1].read", equalTo(true)))
            .andReturn().getResponse().getContentAsString();

        assertThat(fieldNames(objectMapper.readTree(body).get("data"))).containsExactlyInAnyOrder("items", "unreadCount");
    }

    private long createUser(String token) throws Exception {
        String body = mockMvc.perform(get("/api/v1/users/me").header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).at("/data/id").asLong();
    }

    private long insertNotification(long userId, String type, String title, String message, boolean read,
                                    Instant createdAt) {
        return jdbcTemplate.queryForObject("""
            INSERT INTO notifications (user_id, type, title, message, read, created_at)
            VALUES (?, ?, ?, ?, ?, ?) RETURNING id
            """, Long.class, userId, type, title, message, read, Timestamp.from(createdAt));
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
