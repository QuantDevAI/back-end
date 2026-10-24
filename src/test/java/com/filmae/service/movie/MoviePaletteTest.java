package com.filmae.service.movie;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MoviePalette")
class MoviePaletteTest {

    private static final String HEX = "^#[0-9A-F]{6}$";

    @Test
    @DisplayName("Mesmo gênero principal e tconst → mesmas cores")
    void deterministic() {
        MoviePalette first = MoviePalette.of(List.of("Sci-Fi", "Drama"), "tt0816692");
        MoviePalette second = MoviePalette.of(List.of("Sci-Fi", "Drama"), "tt0816692");

        assertThat(second).isEqualTo(first);
    }

    @Test
    @DisplayName("Accent e quatro cores de gradiente em hex #RRGGBB, do escuro para o claro")
    void format() {
        MoviePalette palette = MoviePalette.of("Action", "tt0133093");

        assertThat(palette.accentColor()).matches(HEX);
        assertThat(palette.gradientColors()).hasSize(4).allMatch(color -> color.matches(HEX));
        List<Integer> luminance = palette.gradientColors().stream().map(MoviePaletteTest::luminance).toList();
        assertThat(luminance).isSorted();
    }

    @Test
    @DisplayName("tconst diferente muda as cores; gênero principal muda o matiz")
    void variesByTconstAndGenre() {
        assertThat(MoviePalette.of("Drama", "tt0000001")).isNotEqualTo(MoviePalette.of("Drama", "tt0000002"));
        assertThat(MoviePalette.of("Horror", "tt0000001").accentColor())
            .isNotEqualTo(MoviePalette.of("Family", "tt0000001").accentColor());
    }

    @Test
    @DisplayName("Sem gênero ou gênero desconhecido usa o matiz padrão sem falhar")
    void unknownGenre() {
        assertThat(MoviePalette.of(List.of(), "tt1").accentColor()).matches(HEX);
        assertThat(MoviePalette.of((List<String>) null, null).gradientColors()).hasSize(4);
        assertThat(MoviePalette.of("Inexistente", "tt1")).isEqualTo(MoviePalette.of((String) null, "tt1"));
    }

    @Test
    @DisplayName("Conversão HSL → hex")
    void hslToHex() {
        assertThat(MoviePalette.hsl(0, 100, 50)).isEqualTo("#FF0000");
        assertThat(MoviePalette.hsl(120, 100, 25)).isEqualTo("#008000");
        assertThat(MoviePalette.hsl(240, 100, 50)).isEqualTo("#0000FF");
        assertThat(MoviePalette.hsl(0, 0, 100)).isEqualTo("#FFFFFF");
        assertThat(MoviePalette.hsl(-120, 100, 50)).isEqualTo("#0000FF");
    }

    @Test
    @DisplayName("Hash FNV-1a é estável")
    void stableHash() {
        assertThat(MoviePalette.fnv1a("")).isEqualTo(0x811C9DC5);
        assertThat(MoviePalette.fnv1a("a")).isEqualTo(0xE40C292C);
    }

    private static int luminance(String hex) {
        int r = Integer.parseInt(hex.substring(1, 3), 16);
        int g = Integer.parseInt(hex.substring(3, 5), 16);
        int b = Integer.parseInt(hex.substring(5, 7), 16);
        return r + g + b;
    }
}
