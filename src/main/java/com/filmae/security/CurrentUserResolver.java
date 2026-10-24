package com.filmae.security;

import com.filmae.entity.User;
import com.filmae.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/** Resolve parâmetros anotados com {@link CurrentUser}. */
@Component
@RequiredArgsConstructor
public class CurrentUserResolver implements HandlerMethodArgumentResolver {

    private final CurrentUserService currentUserService;

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        if (!parameter.hasParameterAnnotation(CurrentUser.class)) {
            return false;
        }
        Class<?> type = parameter.getParameterType();
        return User.class.equals(type) || Long.class.equals(type) || AuthenticatedUser.class.equals(type);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        Class<?> type = parameter.getParameterType();
        if (AuthenticatedUser.class.equals(type)) {
            return currentUserService.requireAuthenticatedUser();
        }
        User user = currentUserService.getCurrentUser();
        return Long.class.equals(type) ? user.getId() : user;
    }
}
