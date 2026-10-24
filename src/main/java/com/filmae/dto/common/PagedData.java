package com.filmae.dto.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * Página aninhada dentro de {@code data}: {@code {data: [...], meta: {page, size, totalElements, totalPages}}}.
 * É o formato que {@code movies.service.ts} lê ({@code MovieSearchResult}).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PagedData<T> {

    private List<T> data;
    private PageMeta meta;

    public static <T> PagedData<T> from(Page<T> page) {
        return new PagedData<>(page.getContent(), PageMeta.from(page));
    }

    public static <S, T> PagedData<T> from(Page<S> page, Function<S, T> mapper) {
        return new PagedData<>(page.getContent().stream().map(mapper).toList(), PageMeta.from(page));
    }

    public static <T> PagedData<T> of(List<T> content, Page<?> page) {
        return new PagedData<>(content, PageMeta.from(page));
    }
}
