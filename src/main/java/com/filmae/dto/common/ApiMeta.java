package com.filmae.dto.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.filmae.util.RequestContext;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;

/**
 * {@code meta} do envelope de sucesso. Sempre traz {@code requestId}; os campos de
 * paginação só aparecem na variante paginada ({@link ApiResponse#paged(Page)}).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiMeta {

    private String requestId;
    private Integer page;
    private Integer size;
    private Long totalElements;
    private Integer totalPages;

    public static ApiMeta current() {
        return ApiMeta.builder().requestId(RequestContext.requestId()).build();
    }

    public static ApiMeta currentWithPage(Page<?> page) {
        return ApiMeta.builder()
            .requestId(RequestContext.requestId())
            .page(page.getNumber())
            .size(page.getSize())
            .totalElements(page.getTotalElements())
            .totalPages(page.getTotalPages())
            .build();
    }
}
