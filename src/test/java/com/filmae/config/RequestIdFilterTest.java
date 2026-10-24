package com.filmae.config;

import com.filmae.util.RequestContext;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("RequestIdFilter")
class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    @DisplayName("Gera req_ quando não há header e expõe no MDC, atributo e header de resposta")
    void generatesRequestId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/health");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> mdcInside = new AtomicReference<>();
        AtomicReference<String> contextInside = new AtomicReference<>();

        filter.doFilter(request, response, (req, res) -> {
            mdcInside.set(MDC.get(RequestContext.MDC_KEY));
            contextInside.set(RequestContext.requestId());
        });

        String header = response.getHeader(RequestContext.HEADER);
        assertThat(header).matches("req_[0-9A-Z]{16}");
        assertThat(mdcInside.get()).isEqualTo(header);
        assertThat(contextInside.get()).isEqualTo(header);
        assertThat(request.getAttribute(RequestContext.ATTRIBUTE)).isEqualTo(header);
    }

    @Test
    @DisplayName("Reaproveita X-Request-Id válido recebido")
    void reusesIncomingHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");
        request.addHeader(RequestContext.HEADER, "req_01J9F8Y3");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> mdcInside = new AtomicReference<>();

        filter.doFilter(request, response, (req, res) -> mdcInside.set(MDC.get(RequestContext.MDC_KEY)));

        assertThat(response.getHeader(RequestContext.HEADER)).isEqualTo("req_01J9F8Y3");
        assertThat(mdcInside.get()).isEqualTo("req_01J9F8Y3");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "com espaço", "<script>", "x\nInjected: 1"})
    @DisplayName("Ignora X-Request-Id inválido e gera outro")
    void replacesInvalidHeader(String invalid) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");
        request.addHeader(RequestContext.HEADER, invalid);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> { });

        assertThat(response.getHeader(RequestContext.HEADER)).startsWith("req_").isNotEqualTo(invalid);
    }

    @Test
    @DisplayName("Ignora X-Request-Id com mais de 128 caracteres")
    void replacesTooLongHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");
        request.addHeader(RequestContext.HEADER, "a".repeat(129));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> { });

        assertThat(response.getHeader(RequestContext.HEADER)).startsWith("req_");
    }

    @Test
    @DisplayName("Limpa o MDC depois da requisição, inclusive quando a cadeia lança exceção")
    void clearsMdc() throws Exception {
        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), (req, res) -> { });
        assertThat(MDC.get(RequestContext.MDC_KEY)).isNull();

        FilterChain failing = (req, res) -> {
            throw new IllegalStateException("falhou");
        };
        assertThatThrownBy(() -> filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), failing))
            .isInstanceOf(IllegalStateException.class);
        assertThat(MDC.get(RequestContext.MDC_KEY)).isNull();
    }

    @Test
    @DisplayName("Gera ids diferentes a cada requisição")
    void generatesDistinctIds() throws Exception {
        MockHttpServletResponse first = new MockHttpServletResponse();
        MockHttpServletResponse second = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest(), first, (req, res) -> { });
        filter.doFilter(new MockHttpServletRequest(), second, (req, res) -> { });

        assertThat(first.getHeader(RequestContext.HEADER)).isNotEqualTo(second.getHeader(RequestContext.HEADER));
    }
}
