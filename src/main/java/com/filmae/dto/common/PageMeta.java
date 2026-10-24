package com.filmae.dto.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;

/** Equivale a {@code ApiPaginationMeta} do front. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PageMeta {

    private int page;
    private int size;
    private long totalElements;
    private int totalPages;

    public static PageMeta from(Page<?> page) {
        return PageMeta.builder()
            .page(page.getNumber())
            .size(page.getSize())
            .totalElements(page.getTotalElements())
            .totalPages(page.getTotalPages())
            .build();
    }
}
