package com.filmae.service.movie;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("GenreFilterParser")
class GenreFilterParserTest {

    @Test
    @DisplayName("Formato do Axios: genres[]=Drama&genres[]=Sci-Fi")
    void arrayFormat() {
        assertThat(GenreFilterParser.parse(List.of("Drama", "Sci-Fi"), null)).containsExactly("Drama", "Sci-Fi");
    }

    @Test
    @DisplayName("Formato separado por vírgula: genres=Drama,Sci-Fi")
    void csvFormat() {
        assertThat(GenreFilterParser.parse(null, List.of("Drama,Sci-Fi"))).containsExactly("Drama", "Sci-Fi");
        assertThat(GenreFilterParser.parse(List.of(" Drama , Sci-Fi ,, "))).containsExactly("Drama", "Sci-Fi");
    }

    @Test
    @DisplayName("Os dois formatos juntos são unidos sem duplicar, mantendo a ordem")
    void mixedFormats() {
        assertThat(GenreFilterParser.parse(List.of("Drama", "Sci-Fi"), List.of("Sci-Fi,Horror", "Drama")))
            .containsExactly("Drama", "Sci-Fi", "Horror");
    }

    @Test
    @DisplayName("Nada informado → lista vazia")
    void empty() {
        assertThat(GenreFilterParser.parse(null, null)).isEmpty();
        assertThat(GenreFilterParser.parse(List.of("", " ", ","), Arrays.asList((String) null))).isEmpty();
    }
}
