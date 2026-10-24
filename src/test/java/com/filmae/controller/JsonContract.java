package com.filmae.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/** Conjuntos exatos de chaves esperados pelos tipos do front ({@code src/types/*.ts}). */
final class JsonContract {

    static final Set<String> USER_STATE = Set.of("watchlist", "watched", "favorite", "rewatch", "rating");
    static final Set<String> IMDB = Set.of("rating", "votes");
    static final Set<String> LIST_ITEM = Set.of("id", "title", "year", "runtimeMinutes", "genres", "director", "imdb",
        "compatibilityScore", "accentColor", "gradientColors", "userState");
    static final Set<String> DETAILS = union(LIST_ITEM, Set.of("originalTitle", "type", "cast", "synopsis", "compatibilityReasons"));
    static final Set<String> SUMMARY = Set.of("total", "watched", "watchlist", "favorite", "rewatch", "rated");
    static final Set<String> COLLECTION_MOVIE = Set.of("movie", "summary", "updatedAt");
    static final Set<String> PAGE_META = Set.of("page", "size", "totalElements", "totalPages");

    private JsonContract() {
    }

    static JsonNode body(ObjectMapper mapper, MvcResult result) throws Exception {
        return mapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    static void assertKeys(JsonNode node, Set<String> expected) {
        assertThat(keys(node)).as("chaves de %s", node).isEqualTo(new TreeSet<>(expected));
    }

    static void assertListItem(JsonNode item) {
        assertKeys(item, LIST_ITEM);
        assertKeys(item.get("imdb"), IMDB);
        assertKeys(item.get("userState"), USER_STATE);
        assertThat(item.get("gradientColors")).hasSize(4);
        item.get("gradientColors").forEach(color -> assertThat(color.asText()).matches("^#[0-9A-F]{6}$"));
        assertThat(item.get("accentColor").asText()).matches("^#[0-9A-F]{6}$");
        assertThat(item.get("compatibilityScore").asInt()).isBetween(0, 100);
    }

    static void assertCollectionMovie(JsonNode data) {
        assertKeys(data, COLLECTION_MOVIE);
        assertListItem(data.get("movie"));
        assertKeys(data.get("summary"), SUMMARY);
        assertThat(data.get("updatedAt").asText()).endsWith("Z");
    }

    private static Set<String> keys(JsonNode node) {
        Set<String> keys = new TreeSet<>();
        Iterator<String> names = node.fieldNames();
        names.forEachRemaining(keys::add);
        return keys;
    }

    private static Set<String> union(Set<String> a, Set<String> b) {
        Set<String> result = new TreeSet<>(a);
        result.addAll(b);
        return result;
    }
}
