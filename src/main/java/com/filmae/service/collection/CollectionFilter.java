package com.filmae.service.collection;

import com.filmae.exception.ApiException;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/** {@code CollectionFilter} do front: {@code all | watchlist | watched | favorite | rewatch}. */
public enum CollectionFilter {
    ALL, WATCHLIST, WATCHED, FAVORITE, REWATCH;

    /** {@code null}/vazio → {@link #ALL}; valor desconhecido → 400 VALIDATION_ERROR. */
    public static CollectionFilter parse(String value) {
        if (value == null || value.isBlank()) {
            return ALL;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            String allowed = Arrays.stream(values()).map(f -> f.name().toLowerCase(Locale.ROOT))
                .collect(Collectors.joining(", "));
            throw ApiException.validation("filter", "Filtro inválido. Use: " + allowed);
        }
    }
}
