package com.filmae.service.movie;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Junta os gêneros do filtro de busca vindos de {@code genres[]=Drama&genres[]=Sci-Fi} (padrão do
 * Axios) e de {@code genres=Drama,Sci-Fi}. Valores são separados por vírgula, aparados e
 * deduplicados mantendo a ordem.
 */
public final class GenreFilterParser {

    private GenreFilterParser() {
    }

    @SafeVarargs
    public static List<String> parse(Collection<String>... sources) {
        Set<String> result = new LinkedHashSet<>();
        for (Collection<String> source : sources) {
            if (source == null) {
                continue;
            }
            for (String raw : source) {
                if (raw == null) {
                    continue;
                }
                for (String part : raw.split(",")) {
                    String value = part.trim();
                    if (!value.isEmpty()) {
                        result.add(value);
                    }
                }
            }
        }
        return new ArrayList<>(result);
    }
}
