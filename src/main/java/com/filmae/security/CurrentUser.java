package com.filmae.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Injeta o usuário autenticado em parâmetros de controller. Tipos aceitos:
 * {@link com.filmae.entity.User} (usuário local, criado sob demanda), {@link Long} ({@code users.id})
 * e {@link AuthenticatedUser} (claims do token).
 *
 * <pre>{@code
 * @GetMapping
 * public ApiResponse<Foo> get(@CurrentUser Long userId) { ... }
 * }</pre>
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CurrentUser {
}
