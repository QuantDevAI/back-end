package com.filmae.service;

import com.filmae.dto.common.ApiFieldError;
import com.filmae.entity.GenreCatalog;
import com.filmae.exception.ApiException;
import com.filmae.exception.ErrorCode;
import com.filmae.repository.GenreCatalogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Traduz gêneros recebidos da API (slug do front, ex. {@code ficcao-cientifica}, ou nome do IMDb, ex.
 * {@code Sci-Fi}, sem diferenciar maiúsculas) para o nome do IMDb, usando {@code genre_catalog}.
 */
@Component
@RequiredArgsConstructor
public class GenreResolver {

    private final GenreCatalogRepository genreCatalogRepository;

    /**
     * Resolve a lista inteira, sem duplicatas e na ordem recebida (ex.: {@code suspense} e {@code thriller}
     * viram um único {@code Thriller}). Itens inválidos geram {@code VALIDATION_ERROR} com
     * {@code fields[].field = "<field>[i]"}.
     */
    @Transactional(readOnly = true)
    public List<String> resolveAll(List<String> values, String field) {
        Map<String, String> table = lookupTable();
        LinkedHashSet<String> resolved = new LinkedHashSet<>();
        List<ApiFieldError> errors = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) {
            String value = values.get(i);
            String imdbName = StringUtils.hasText(value) ? table.get(normalize(value)) : null;
            if (imdbName == null) {
                errors.add(ApiFieldError.of(field + "[" + i + "]", "Gênero inválido: " + (value == null ? "null" : value)));
            } else {
                resolved.add(imdbName);
            }
        }
        if (!errors.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Há gêneros inválidos", errors);
        }
        return List.copyOf(resolved);
    }

    private Map<String, String> lookupTable() {
        List<GenreCatalog> catalog = genreCatalogRepository.findAllByOrderBySortOrderAsc();
        Map<String, String> table = new HashMap<>();
        catalog.forEach(genre -> table.putIfAbsent(normalize(genre.getImdbName()), genre.getImdbName()));
        catalog.forEach(genre -> table.putIfAbsent(normalize(genre.getSlug()), genre.getImdbName()));
        return table;
    }

    private static String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
