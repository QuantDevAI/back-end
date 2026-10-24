package com.filmae.service.movie;

import com.filmae.entity.GenreCatalog;
import com.filmae.repository.GenreCatalogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Traduz gêneros entre slug do front, nome IMDb e nome em português usando {@code genre_catalog}.
 * A tabela é estática (seed da migration), então fica em cache depois da primeira leitura.
 */
@Component
@RequiredArgsConstructor
public class GenreNameResolver {

    private final GenreCatalogRepository genreCatalogRepository;

    private volatile Catalog catalog;

    /** Slug ({@code ficcao-cientifica}) ou nome IMDb em qualquer caixa ({@code sci-fi}) → {@code Sci-Fi}. Desconhecido volta como veio. */
    public String toImdbName(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        String key = trimmed.toLowerCase(Locale.ROOT);
        Catalog c = catalog();
        String bySlug = c.imdbBySlug().get(key);
        if (bySlug != null) {
            return bySlug;
        }
        String byName = c.imdbByLowerName().get(key);
        return byName != null ? byName : trimmed;
    }

    public List<String> toImdbNames(List<String> values) {
        return values.stream().map(this::toImdbName).distinct().toList();
    }

    /** Nome em português do gênero IMDb ({@code Sci-Fi} → {@code Ficção Científica}). */
    public String namePt(String imdbName) {
        if (imdbName == null) {
            return null;
        }
        GenreCatalog entry = catalog().canonicalByImdb().get(imdbName);
        return entry != null ? entry.getNamePt() : imdbName;
    }

    /** Entradas canônicas (uma por gênero IMDb), na ordem de {@code sort_order}. */
    public List<GenreCatalog> canonicalGenres() {
        return List.copyOf(catalog().canonicalByImdb().values());
    }

    private Catalog catalog() {
        Catalog current = catalog;
        if (current == null) {
            current = load(genreCatalogRepository.findAllByOrderBySortOrderAsc());
            catalog = current;
        }
        return current;
    }

    private static Catalog load(List<GenreCatalog> rows) {
        Map<String, String> imdbBySlug = new LinkedHashMap<>();
        Map<String, String> imdbByLowerName = new LinkedHashMap<>();
        Map<String, GenreCatalog> canonicalByImdb = new LinkedHashMap<>();
        rows.stream()
            .sorted(Comparator.comparingInt(GenreCatalog::getSortOrder))
            .forEach(row -> {
                imdbBySlug.put(row.getSlug().toLowerCase(Locale.ROOT), row.getImdbName());
                imdbByLowerName.putIfAbsent(row.getImdbName().toLowerCase(Locale.ROOT), row.getImdbName());
                if (row.isCanonical()) {
                    canonicalByImdb.putIfAbsent(row.getImdbName(), row);
                }
            });
        return new Catalog(imdbBySlug, imdbByLowerName, canonicalByImdb);
    }

    private record Catalog(Map<String, String> imdbBySlug,
                           Map<String, String> imdbByLowerName,
                           Map<String, GenreCatalog> canonicalByImdb) {
    }
}
