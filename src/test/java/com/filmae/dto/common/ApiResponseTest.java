package com.filmae.dto.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ApiResponse")
class ApiResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Page<String> page = new PageImpl<>(List.of("tt1", "tt2"), PageRequest.of(1, 2), 5);

    @BeforeEach
    void setRequestId() {
        MDC.put("requestId", "req_RESP");
    }

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    @DisplayName("of(): {data, meta:{requestId}} sem campos de paginação")
    void of() throws Exception {
        JsonNode json = objectMapper.valueToTree(ApiResponse.of(Map.of("status", "UP")));

        assertThat(json.get("data").get("status").asText()).isEqualTo("UP");
        assertThat(json.get("meta").get("requestId").asText()).isEqualTo("req_RESP");
        assertThat(json.get("meta").size()).isEqualTo(1);
    }

    @Test
    @DisplayName("paged(): lista em data e paginação + requestId em meta")
    void paged() {
        JsonNode json = objectMapper.valueToTree(ApiResponse.paged(page));

        assertThat(json.get("data").isArray()).isTrue();
        assertThat(json.get("data")).hasSize(2);
        JsonNode meta = json.get("meta");
        assertThat(meta.get("requestId").asText()).isEqualTo("req_RESP");
        assertThat(meta.get("page").asInt()).isEqualTo(1);
        assertThat(meta.get("size").asInt()).isEqualTo(2);
        assertThat(meta.get("totalElements").asLong()).isEqualTo(5);
        assertThat(meta.get("totalPages").asInt()).isEqualTo(3);
    }

    @Test
    @DisplayName("nestedPage(): {data:{data:[...], meta:{page...}}, meta:{requestId}} (formato do GET /movies)")
    void nestedPage() {
        JsonNode json = objectMapper.valueToTree(ApiResponse.nestedPage(page));

        JsonNode inner = json.get("data");
        assertThat(inner.get("data").get(0).asText()).isEqualTo("tt1");
        assertThat(inner.get("meta").get("page").asInt()).isEqualTo(1);
        assertThat(inner.get("meta").get("size").asInt()).isEqualTo(2);
        assertThat(inner.get("meta").get("totalElements").asLong()).isEqualTo(5);
        assertThat(inner.get("meta").get("totalPages").asInt()).isEqualTo(3);
        assertThat(inner.get("meta").has("requestId")).isFalse();

        JsonNode outerMeta = json.get("meta");
        assertThat(outerMeta.get("requestId").asText()).isEqualTo("req_RESP");
        assertThat(outerMeta.has("page")).isFalse();
    }

    @Test
    @DisplayName("PagedData.from(page, mapper) converte o conteúdo e mantém a paginação")
    void pagedDataMapper() {
        PagedData<Integer> mapped = PagedData.from(page, String::length);

        assertThat(mapped.getData()).containsExactly(3, 3);
        assertThat(mapped.getMeta().getTotalElements()).isEqualTo(5);
    }

    @Test
    @DisplayName("Página vazia: data [] e totalPages 0")
    void emptyPage() {
        JsonNode json = objectMapper.valueToTree(ApiResponse.nestedPage(Page.<String>empty(PageRequest.of(0, 20))));

        assertThat(json.get("data").get("data")).isEmpty();
        assertThat(json.get("data").get("meta").get("totalPages").asInt()).isZero();
        assertThat(json.get("data").get("meta").get("size").asInt()).isEqualTo(20);
    }

    @Test
    @DisplayName("ok() e created() devolvem 200 e 201 com o envelope")
    void responseEntities() {
        assertThat(ApiResponse.ok("x").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ApiResponse.created("x").getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(ApiResponse.created("x").getBody().getMeta().getRequestId()).isEqualTo("req_RESP");
    }

    @Test
    @DisplayName("Sem requisição em andamento, gera um requestId novo")
    void generatesRequestIdOutsideRequest() {
        MDC.clear();
        assertThat(ApiResponse.of("x").getMeta().getRequestId()).matches("req_[0-9A-Z]{16}");
    }
}
