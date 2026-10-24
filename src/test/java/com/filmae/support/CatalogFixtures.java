package com.filmae.support;

import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;

/** Insere filmes de teste direto nas tabelas do catálogo (o perfil test não carrega o seed de dev). */
public class CatalogFixtures {

    private final JdbcTemplate jdbc;

    public CatalogFixtures(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public MovieBuilder movie(String tconst) {
        return new MovieBuilder(tconst);
    }

    public class MovieBuilder {
        private final String tconst;
        private String title = "Movie";
        private String titlePt;
        private String titleType = "movie";
        private Integer year = 2000;
        private Integer runtime = 120;
        private List<String> genres = List.of("Drama");
        private BigDecimal rating = new BigDecimal("7.5");
        private int votes = 100_000;
        private String directorNconst;
        private String directorName;
        private List<String> cast = List.of();

        private MovieBuilder(String tconst) {
            this.tconst = tconst;
        }

        public MovieBuilder title(String title) { this.title = title; return this; }
        public MovieBuilder titlePt(String titlePt) { this.titlePt = titlePt; return this; }
        public MovieBuilder titleType(String titleType) { this.titleType = titleType; return this; }
        public MovieBuilder year(Integer year) { this.year = year; return this; }
        public MovieBuilder runtime(Integer runtime) { this.runtime = runtime; return this; }
        public MovieBuilder genres(String... genres) { this.genres = List.of(genres); return this; }
        public MovieBuilder rating(String rating, int votes) { this.rating = new BigDecimal(rating); this.votes = votes; return this; }
        public MovieBuilder noRating() { this.rating = null; return this; }
        public MovieBuilder director(String nconst, String name) { this.directorNconst = nconst; this.directorName = name; return this; }
        /** Nomes do elenco; os nconsts são derivados do tconst. */
        public MovieBuilder cast(String... names) { this.cast = List.of(names); return this; }

        public String insert() {
            jdbc.update("""
                INSERT INTO filmes (tconst, title_type, primary_title, original_title, is_adult, start_year, runtime_minutes, genres)
                VALUES (?, ?, ?, ?, FALSE, ?, ?, ?::text[])
                """, tconst, titleType, title, title, year, runtime, toArrayLiteral(genres));
            if (rating != null) {
                jdbc.update("INSERT INTO avaliacoes_filmes (tconst, average_rating, num_votes) VALUES (?, ?, ?)",
                    tconst, rating, votes);
            }
            if (titlePt != null) {
                jdbc.update("""
                    INSERT INTO titulos_alternativos (title_id, ordering, title, region, language, is_original_title)
                    VALUES (?, 1, ?, 'BR', 'pt', FALSE)
                    """, tconst, titlePt);
            }
            int ordering = 1;
            if (directorNconst != null) {
                upsertPerson(directorNconst, directorName);
                jdbc.update("INSERT INTO equipe_filme (tconst, directors) VALUES (?, ?::text[])",
                    tconst, toArrayLiteral(List.of(directorNconst)));
                jdbc.update("INSERT INTO principais_filme (tconst, ordering, nconst, category) VALUES (?, ?, ?, 'director')",
                    tconst, ordering++, directorNconst);
            }
            for (int i = 0; i < cast.size(); i++) {
                String nconst = "nm" + tconst.substring(2) + i;
                upsertPerson(nconst, cast.get(i));
                jdbc.update("INSERT INTO principais_filme (tconst, ordering, nconst, category) VALUES (?, ?, ?, 'actor')",
                    tconst, ordering++, nconst);
            }
            return tconst;
        }

        private void upsertPerson(String nconst, String name) {
            jdbc.update("INSERT INTO pessoas (nconst, primary_name) VALUES (?, ?) ON CONFLICT (nconst) DO NOTHING",
                nconst, name);
        }
    }

    private static String toArrayLiteral(List<String> values) {
        return "{" + String.join(",", values.stream().map(v -> "\"" + v.replace("\"", "\\\"") + "\"").toList()) + "}";
    }
}
