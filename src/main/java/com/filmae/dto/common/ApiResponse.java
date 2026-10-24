package com.filmae.dto.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

/**
 * Envelope de sucesso do contrato: {@code {data, meta: {requestId}}}.
 *
 * <ul>
 *   <li>{@link #of(Object)}: {@code {data, meta: {requestId}}}</li>
 *   <li>{@link #paged(Page)}: {@code {data: [...], meta: {requestId, page, size, totalElements, totalPages}}}</li>
 *   <li>{@link #nestedPage(Page)}: {@code {data: {data: [...], meta: {page...}}, meta: {requestId}}} (usado por GET /movies)</li>
 * </ul>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private T data;
    private ApiMeta meta;

    public static <T> ApiResponse<T> of(T data) {
        return new ApiResponse<>(data, ApiMeta.current());
    }

    public static <T> ApiResponse<List<T>> paged(Page<T> page) {
        return new ApiResponse<>(page.getContent(), ApiMeta.currentWithPage(page));
    }

    public static <T> ApiResponse<PagedData<T>> nestedPage(Page<T> page) {
        return of(PagedData.from(page));
    }

    public static <T> ResponseEntity<ApiResponse<T>> ok(T data) {
        return ResponseEntity.ok(of(data));
    }

    public static <T> ResponseEntity<ApiResponse<T>> created(T data) {
        return ResponseEntity.status(HttpStatus.CREATED).body(of(data));
    }
}
