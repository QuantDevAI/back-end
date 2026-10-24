package com.filmae.service;

import com.filmae.client.AuthServiceClient;
import com.filmae.client.dto.AuthLoginRequest;
import com.filmae.client.dto.AuthRefreshRequest;
import com.filmae.client.dto.AuthRegisterRequest;
import com.filmae.client.dto.AuthTokenResponse;
import com.filmae.client.dto.AuthUserResponse;
import com.filmae.dto.auth.AuthSessionResponse;
import com.filmae.dto.auth.AuthSessionResponse.NextScreen;
import com.filmae.dto.auth.GoogleLoginRequest;
import com.filmae.dto.auth.LoginRequest;
import com.filmae.dto.auth.RefreshSessionRequest;
import com.filmae.dto.auth.RefreshSessionResponse;
import com.filmae.dto.auth.RegisterRequest;
import com.filmae.entity.User;
import com.filmae.exception.ApiException;
import com.filmae.exception.ErrorCode;
import com.filmae.security.AuthenticatedUser;
import com.filmae.security.JwtProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthFacadeService")
class AuthFacadeServiceTest {

    private static final UUID AUTH_ID = UUID.randomUUID();

    @Mock private AuthServiceClient authServiceClient;
    @Mock private JwtProvider jwtProvider;
    @Mock private CurrentUserService currentUserService;
    @InjectMocks private AuthFacadeService service;

    private static AuthTokenResponse tokens(Long expiresInMs) {
        return new AuthTokenResponse("access", "refresh", "Bearer", expiresInMs);
    }

    private static AuthenticatedUser claims() {
        return new AuthenticatedUser(AUTH_ID, "ana@example.com", "Ana", List.of("ROLE_USER"), "access");
    }

    private static User localUser(Instant onboardingCompletedAt) {
        return User.builder().id(7L).authUserId(AUTH_ID).email("ana@example.com").name("Ana")
            .onboardingCompletedAt(onboardingCompletedAt).build();
    }

    @ParameterizedTest(name = "{0} ms -> {1} s")
    @CsvSource({"900000, 900", "3600000, 3600", "1999, 1", "999, 0", "0, 0"})
    @DisplayName("expiresIn do auth (ms) vira segundos")
    void expiresInConversion(long ms, long seconds) {
        assertThat(AuthFacadeService.toSeconds(ms)).isEqualTo(seconds);
    }

    @Test
    @DisplayName("expiresIn ausente ou negativo usa o padrão de 900 s")
    void expiresInFallback() {
        assertThat(AuthFacadeService.toSeconds(null)).isEqualTo(900);
        assertThat(AuthFacadeService.toSeconds(-5L)).isEqualTo(900);
    }

    @Test
    @DisplayName("login: usa claims do JWT para o usuário local e nextScreen=onboarding se não concluiu")
    void loginOnboardingPending() {
        when(authServiceClient.login(any())).thenReturn(tokens(900000L));
        when(jwtProvider.parseAccessToken("access")).thenReturn(Optional.of(claims()));
        when(currentUserService.findOrCreate(AUTH_ID, "ana@example.com", "Ana")).thenReturn(localUser(null));

        AuthSessionResponse session = service.login(new LoginRequest(" ana@example.com ", "Senha@123"));

        verify(authServiceClient).login(new AuthLoginRequest("ana@example.com", "Senha@123"));
        assertThat(session.accessToken()).isEqualTo("access");
        assertThat(session.refreshToken()).isEqualTo("refresh");
        assertThat(session.expiresIn()).isEqualTo(900);
        assertThat(session.nextScreen()).isEqualTo(NextScreen.ONBOARDING);
        assertThat(session.user().id()).isEqualTo(7L);
        assertThat(session.user().onboardingCompleted()).isFalse();
    }

    @Test
    @DisplayName("login: nextScreen=home quando o onboarding já foi concluído")
    void loginOnboardingDone() {
        when(authServiceClient.login(any())).thenReturn(tokens(900000L));
        when(jwtProvider.parseAccessToken("access")).thenReturn(Optional.of(claims()));
        when(currentUserService.findOrCreate(any(), any(), any())).thenReturn(localUser(Instant.now()));

        AuthSessionResponse session = service.login(new LoginRequest("ana@example.com", "Senha@123"));

        assertThat(session.nextScreen()).isEqualTo(NextScreen.HOME);
        assertThat(session.user().onboardingCompleted()).isTrue();
    }

    @Test
    @DisplayName("nextScreen segue onboardingCompleted")
    void nextScreenRule() {
        assertThat(NextScreen.forOnboardingCompleted(true).getValue()).isEqualTo("home");
        assertThat(NextScreen.forOnboardingCompleted(false).getValue()).isEqualTo("onboarding");
    }

    @Test
    @DisplayName("login: token do auth rejeitado pelo JwtProvider vira DEPENDENCY_UNAVAILABLE")
    void loginWithUnverifiableToken() {
        when(authServiceClient.login(any())).thenReturn(tokens(900000L));
        when(jwtProvider.parseAccessToken("access")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(new LoginRequest("ana@example.com", "Senha@123")))
            .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.DEPENDENCY_UNAVAILABLE));
        verifyNoInteractions(currentUserService);
    }

    @Test
    @DisplayName("login: erro do auth é propagado sem criar usuário local")
    void loginErrorPropagates() {
        when(authServiceClient.login(any())).thenThrow(ApiException.unauthorized("E-mail ou senha inválidos"));

        assertThatThrownBy(() -> service.login(new LoginRequest("ana@example.com", "x")))
            .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
        verifyNoInteractions(jwtProvider, currentUserService);
    }

    @Test
    @DisplayName("register: chama register e depois login no auth e não devolve nextScreen")
    void registerThenLogin() {
        when(authServiceClient.register(any())).thenReturn(new AuthUserResponse(AUTH_ID, "Ana", "ana@example.com", "PENDING_VERIFICATION"));
        when(authServiceClient.login(any())).thenReturn(tokens(900000L));
        when(jwtProvider.parseAccessToken("access")).thenReturn(Optional.of(claims()));
        when(currentUserService.findOrCreate(AUTH_ID, "ana@example.com", "Ana")).thenReturn(localUser(null));

        AuthSessionResponse session = service.register(new RegisterRequest(" Ana ", "ana@example.com", "Senha@123"));

        InOrder order = inOrder(authServiceClient);
        order.verify(authServiceClient).register(new AuthRegisterRequest("Ana", "ana@example.com", "Senha@123"));
        order.verify(authServiceClient).login(new AuthLoginRequest("ana@example.com", "Senha@123"));
        assertThat(session.nextScreen()).isNull();
        assertThat(session.expiresIn()).isEqualTo(900);
        assertThat(session.user().email()).isEqualTo("ana@example.com");
    }

    @Test
    @DisplayName("register: 409 do auth não tenta login")
    void registerConflictStops() {
        when(authServiceClient.register(any())).thenThrow(new ApiException(ErrorCode.EMAIL_ALREADY_EXISTS));

        assertThatThrownBy(() -> service.register(new RegisterRequest("Ana", "ana@example.com", "Senha@123")))
            .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS));
        verify(authServiceClient, never()).login(any());
    }

    @Test
    @DisplayName("refresh: repassa o token e converte expiresIn")
    void refresh() {
        when(authServiceClient.refresh(new AuthRefreshRequest("rt"))).thenReturn(tokens(1800000L));

        RefreshSessionResponse response = service.refresh(new RefreshSessionRequest("rt"));

        assertThat(response.accessToken()).isEqualTo("access");
        assertThat(response.refreshToken()).isEqualTo("refresh");
        assertThat(response.expiresIn()).isEqualTo(1800);
    }

    @Test
    @DisplayName("logout: repassa o access token do usuário")
    void logout() {
        service.logout(claims());
        verify(authServiceClient).logout("access");
    }

    @Test
    @DisplayName("google: sempre DEPENDENCY_UNAVAILABLE (503)")
    void googleUnavailable() {
        assertThatThrownBy(() -> service.loginWithGoogle(new GoogleLoginRequest("id-token")))
            .isInstanceOfSatisfying(ApiException.class, ex -> {
                assertThat(ex.getCode()).isEqualTo(ErrorCode.DEPENDENCY_UNAVAILABLE);
                assertThat(ex.getStatus().value()).isEqualTo(503);
            });
        verifyNoInteractions(authServiceClient);
    }
}
