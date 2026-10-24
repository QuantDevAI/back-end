package com.filmae.dto.onboarding;

import java.util.List;

/** Aceita slugs do front ({@code ficcao-cientifica}) ou nomes do IMDb ({@code Sci-Fi}). */
public record SaveGenresRequest(List<String> genres) {
}
