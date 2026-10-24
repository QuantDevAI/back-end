package com.filmae.service;

import com.filmae.entity.User;
import com.filmae.exception.ApiException;
import com.filmae.repository.UserPreferencesRepository;
import com.filmae.repository.UserRepository;
import com.filmae.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Optional;
import java.util.UUID;

/**
 * Liga o {@code sub} (UUID) do JWT ao usuário local ({@code users.id}). Se o usuário ainda não
 * existir, ele é criado na hora com o e-mail e o nome do token, junto com as preferências padrão.
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class CurrentUserService {

    private final UserRepository userRepository;
    private final UserPreferencesRepository userPreferencesRepository;

    /** Usuário do token da requisição corrente, se autenticada. */
    @Transactional(readOnly = true)
    public Optional<AuthenticatedUser> findAuthenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return Optional.of(user);
        }
        return Optional.empty();
    }

    /** Usuário do token ou {@code 401 UNAUTHORIZED}. */
    @Transactional(readOnly = true)
    public AuthenticatedUser requireAuthenticatedUser() {
        return findAuthenticatedUser().orElseThrow(() -> ApiException.unauthorized("Autenticação necessária"));
    }

    /** Usuário local da requisição corrente (criado sob demanda). */
    public User getCurrentUser() {
        AuthenticatedUser authUser = requireAuthenticatedUser();
        return findOrCreate(authUser.authUserId(), authUser.email(), authUser.name());
    }

    /** {@code users.id} da requisição corrente (criado sob demanda). */
    public Long getCurrentUserId() {
        return getCurrentUser().getId();
    }

    /**
     * Busca o usuário local pelo id do auth ou cria se não existir. Também usado pela fachada de auth
     * após register/login. Atualiza o e-mail se mudou no auth; o nome local não é sobrescrito, porque
     * pode ter sido editado no perfil.
     */
    public User findOrCreate(UUID authUserId, String email, String name) {
        Optional<User> existing = userRepository.findByAuthUserId(authUserId);
        if (existing.isPresent()) {
            User user = existing.get();
            if (StringUtils.hasText(email) && !email.equalsIgnoreCase(user.getEmail())) {
                user.setEmail(email);
            }
            return user;
        }

        String safeEmail = StringUtils.hasText(email) ? email : authUserId + "@users.filmae.local";
        String safeName = StringUtils.hasText(name) ? name : defaultName(safeEmail);
        if (userRepository.insertIfAbsent(authUserId, safeEmail, safeName) > 0) {
            log.info("Usuário local criado para authUserId={}", authUserId);
        }

        User user = userRepository.findByAuthUserId(authUserId)
            .orElseThrow(() -> new IllegalStateException("Usuário local não encontrado após inserção: " + authUserId));
        userPreferencesRepository.insertDefaultsIfAbsent(user.getId());
        return user;
    }

    private static String defaultName(String email) {
        int at = email.indexOf('@');
        String local = at > 0 ? email.substring(0, at) : email;
        return StringUtils.hasText(local) ? local : "Usuário";
    }
}
