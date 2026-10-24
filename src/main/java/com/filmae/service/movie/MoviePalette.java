package com.filmae.service.movie;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Cores do card de um filme ({@code accentColor} + quatro {@code gradientColors}, do mais escuro ao
 * mais claro). O matiz vem do gênero principal e é deslocado por um hash estável do {@code tconst},
 * então o mesmo filme sempre tem as mesmas cores e filmes do mesmo gênero ficam parecidos.
 */
public record MoviePalette(String accentColor, List<String> gradientColors) {

    static final int DEFAULT_HUE = 215;

    private static final Map<String, Integer> GENRE_HUES = Map.ofEntries(
        Map.entry("Action", 0),
        Map.entry("Adventure", 28),
        Map.entry("Animation", 48),
        Map.entry("Comedy", 42),
        Map.entry("Crime", 205),
        Map.entry("Documentary", 150),
        Map.entry("Drama", 265),
        Map.entry("Family", 95),
        Map.entry("Fantasy", 285),
        Map.entry("Sci-Fi", 215),
        Map.entry("Horror", 350),
        Map.entry("Romance", 330),
        Map.entry("Thriller", 190),
        Map.entry("Mystery", 245),
        Map.entry("Biography", 30),
        Map.entry("History", 35),
        Map.entry("Music", 300),
        Map.entry("Musical", 310),
        Map.entry("Sport", 120),
        Map.entry("War", 15),
        Map.entry("Western", 25),
        Map.entry("Film-Noir", 230),
        Map.entry("Short", 175),
        Map.entry("News", 200),
        Map.entry("Reality-TV", 320),
        Map.entry("Talk-Show", 55),
        Map.entry("Game-Show", 60)
    );

    public static MoviePalette of(String primaryGenre, String tconst) {
        int hash = fnv1a(tconst != null ? tconst : "");
        int baseHue = primaryGenre != null ? GENRE_HUES.getOrDefault(primaryGenre, DEFAULT_HUE) : DEFAULT_HUE;
        int hue = Math.floorMod(baseHue + Math.floorMod(hash, 31) - 15, 360);
        int satShift = Math.floorMod(hash >>> 8, 11) - 5;
        int lightShift = Math.floorMod(hash >>> 16, 7) - 3;

        String accent = hsl(hue, 78 + satShift, 52 + lightShift);
        List<String> gradient = List.of(
            hsl(hue - 8, 45 + satShift, 7),
            hsl(hue - 4, 45 + satShift, 15),
            hsl(hue, 48 + satShift, 27),
            hsl(hue + 6, 52 + satShift, 45 + lightShift)
        );
        return new MoviePalette(accent, gradient);
    }

    public static MoviePalette of(List<String> genres, String tconst) {
        return of(genres == null || genres.isEmpty() ? null : genres.get(0), tconst);
    }

    /** FNV-1a 32 bits sobre UTF-8: precisa ser estável entre execuções e JVMs. */
    static int fnv1a(String value) {
        int hash = 0x811C9DC5;
        for (byte b : value.getBytes(StandardCharsets.UTF_8)) {
            hash ^= b & 0xFF;
            hash *= 0x01000193;
        }
        return hash;
    }

    static String hsl(int hue, int saturation, int lightness) {
        double h = Math.floorMod(hue, 360) / 360.0;
        double s = clamp(saturation) / 100.0;
        double l = clamp(lightness) / 100.0;

        double r;
        double g;
        double b;
        if (s == 0) {
            r = g = b = l;
        } else {
            double q = l < 0.5 ? l * (1 + s) : l + s - l * s;
            double p = 2 * l - q;
            r = hueToRgb(p, q, h + 1.0 / 3);
            g = hueToRgb(p, q, h);
            b = hueToRgb(p, q, h - 1.0 / 3);
        }
        return String.format("#%02X%02X%02X", Math.round(r * 255), Math.round(g * 255), Math.round(b * 255));
    }

    private static double hueToRgb(double p, double q, double t) {
        if (t < 0) t += 1;
        if (t > 1) t -= 1;
        if (t < 1.0 / 6) return p + (q - p) * 6 * t;
        if (t < 1.0 / 2) return q;
        if (t < 2.0 / 3) return p + (q - p) * (2.0 / 3 - t) * 6;
        return p;
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(100, value));
    }
}
