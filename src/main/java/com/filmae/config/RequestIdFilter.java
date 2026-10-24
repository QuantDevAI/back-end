package com.filmae.config;

import com.filmae.util.RequestContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.regex.Pattern;

/**
 * Lê o {@code X-Request-Id} recebido (ou gera um novo), guarda no MDC e em atributo da
 * requisição e devolve no header da resposta. Roda antes do Spring Security para que
 * os 401/403 também carreguem o requestId.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    private static final Pattern VALID_ID = Pattern.compile("[A-Za-z0-9._:-]{1,128}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String incoming = request.getHeader(RequestContext.HEADER);
        String requestId = incoming != null && VALID_ID.matcher(incoming).matches()
            ? incoming
            : RequestContext.newRequestId();

        request.setAttribute(RequestContext.ATTRIBUTE, requestId);
        response.setHeader(RequestContext.HEADER, requestId);
        MDC.put(RequestContext.MDC_KEY, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(RequestContext.MDC_KEY);
        }
    }
}
