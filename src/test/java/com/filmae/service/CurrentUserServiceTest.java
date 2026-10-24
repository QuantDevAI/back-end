package com.filmae.service;

import com.filmae.entity.User;
import com.filmae.exception.ApiException;
import com.filmae.exception.ErrorCode;
import com.filmae.repository.UserPreferencesRepository;
import com.filmae.repository.UserRepository;
import com.filmae.security.AuthenticatedUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CurrentUserService")
class CurrentUserServiceTest {

    private static final UUID AUTH_ID = UUID.fromString("7ab33ba2-e81c-4612-a79a-10edf548e697");

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserPreferencesRepository userPreferencesRepository;

    @InjectMocks
    private CurrentUserService service;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Usuário existente é devolvido sem inserir nada")
    void returnsExistingUser() {
        User existing = user(10L, "ana@example.com", "Ana");
        when(userRepository.findByAuthUserId(AUTH_ID)).thenReturn(Optional.of(existing));

        User result = service.findOrCreate(AUTH_ID, "ana@example.com", "Ana");

        assertThat(result).isSameAs(existing);
        verify(userRepository, never()).insertIfAbsent(any(), anyString(), anyString());
        verify(userPreferencesRepository, never()).insertDefaultsIfAbsent(anyLong());
    }

    @Test
    @DisplayName("Não sobrescreve o nome local, mas atualiza o e-mail se mudou no auth")
    void doesNotOverwriteName() {
        User existing = user(10L, "ana@example.com", "Ana Editada no Perfil");
        when(userRepository.findByAuthUserId(AUTH_ID)).thenReturn(Optional.of(existing));

        User result = service.findOrCreate(AUTH_ID, "ana.nova@example.com", "Ana do Token");

        assertThat(result.getName()).isEqualTo("Ana Editada no Perfil");
        assertThat(result.getEmail()).isEqualTo("ana.nova@example.com");
    }

    @Test
    @DisplayName("Mantém o e-mail se o token vier sem e-mail")
    void keepsEmailWhenTokenHasNone() {
        User existing = user(10L, "ana@example.com", "Ana");
        when(userRepository.findByAuthUserId(AUTH_ID)).thenReturn(Optional.of(existing));

        assertThat(service.findOrCreate(AUTH_ID, null, null).getEmail()).isEqualTo("ana@example.com");
    }

    @Test
    @DisplayName("Cria o usuário sob demanda com e-mail e nome do token e as preferências padrão")
    void createsOnDemand() {
        User created = user(42L, "bia@example.com", "Bia");
        when(userRepository.findByAuthUserId(AUTH_ID)).thenReturn(Optional.empty(), Optional.of(created));
        when(userRepository.insertIfAbsent(AUTH_ID, "bia@example.com", "Bia")).thenReturn(1);

        User result = service.findOrCreate(AUTH_ID, "bia@example.com", "Bia");

        assertThat(result.getId()).isEqualTo(42L);
        verify(userRepository).insertIfAbsent(AUTH_ID, "bia@example.com", "Bia");
        verify(userPreferencesRepository).insertDefaultsIfAbsent(42L);
    }

    @Test
    @DisplayName("Concorrência: se outra requisição inseriu antes, usa o registro existente")
    void handlesConcurrentInsert() {
        User created = user(42L, "bia@example.com", "Bia");
        when(userRepository.findByAuthUserId(AUTH_ID)).thenReturn(Optional.empty(), Optional.of(created));
        when(userRepository.insertIfAbsent(any(), anyString(), anyString())).thenReturn(0);

        assertThat(service.findOrCreate(AUTH_ID, "bia@example.com", "Bia").getId()).isEqualTo(42L);
    }

    @Test
    @DisplayName("Sem nome no token, usa a parte local do e-mail; sem e-mail, gera um e-mail técnico")
    void fallbacksForMissingClaims() {
        User created = user(1L, "x", "x");
        when(userRepository.findByAuthUserId(AUTH_ID)).thenReturn(Optional.empty(), Optional.of(created));

        service.findOrCreate(AUTH_ID, "carlos@example.com", "  ");
        verify(userRepository).insertIfAbsent(AUTH_ID, "carlos@example.com", "carlos");

        UUID other = UUID.randomUUID();
        when(userRepository.findByAuthUserId(other)).thenReturn(Optional.empty(), Optional.of(created));
        service.findOrCreate(other, null, null);
        verify(userRepository).insertIfAbsent(other, other + "@users.filmae.local", other.toString());
    }

    @Test
    @DisplayName("getCurrentUser/getCurrentUserId usam o principal do SecurityContext")
    void resolvesFromSecurityContext() {
        authenticate(new AuthenticatedUser(AUTH_ID, "ana@example.com", "Ana", List.of("ROLE_USER"), "tok"));
        when(userRepository.findByAuthUserId(AUTH_ID)).thenReturn(Optional.of(user(7L, "ana@example.com", "Ana")));

        assertThat(service.getCurrentUserId()).isEqualTo(7L);
        assertThat(service.requireAuthenticatedUser().token()).isEqualTo("tok");
    }

    @Test
    @DisplayName("Sem autenticação → ApiException UNAUTHORIZED")
    void unauthenticated() {
        assertThat(service.findAuthenticatedUser()).isEmpty();
        assertThatThrownBy(() -> service.getCurrentUser())
            .isInstanceOf(ApiException.class)
            .extracting(ex -> ((ApiException) ex).getCode())
            .isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Principal de outro tipo (ex.: anônimo) não conta como autenticado")
    void ignoresForeignPrincipal() {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("anonymousUser", null, List.of()));

        assertThat(service.findAuthenticatedUser()).isEmpty();
    }

    private static void authenticate(AuthenticatedUser principal) {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(principal, principal.token(), List.of()));
    }

    private static User user(Long id, String email, String name) {
        return User.builder().id(id).authUserId(AUTH_ID).email(email).name(name).build();
    }
}
