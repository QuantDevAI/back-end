package com.filmae.util;

import org.slf4j.MDC;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.security.SecureRandom;

/** Acesso ao requestId da requisição corrente (preenchido pelo {@code RequestIdFilter}). */
public final class RequestContext {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";
    public static final String ATTRIBUTE = RequestContext.class.getName() + ".requestId";

    private static final char[] ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private RequestContext() {
    }

    /** requestId corrente; se não houver requisição em andamento, gera um novo. */
    public static String requestId() {
        String fromMdc = MDC.get(MDC_KEY);
        if (fromMdc != null) {
            return fromMdc;
        }
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            Object value = attributes.getAttribute(ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
            if (value instanceof String id) {
                return id;
            }
        }
        return newRequestId();
    }

    /** Gera um id no formato {@code req_XXXXXXXXXXXXXXXX} (16 caracteres Crockford base32). */
    public static String newRequestId() {
        char[] chars = new char[16];
        for (int i = 0; i < chars.length; i++) {
            chars[i] = ALPHABET[RANDOM.nextInt(ALPHABET.length)];
        }
        return "req_" + new String(chars);
    }
}
