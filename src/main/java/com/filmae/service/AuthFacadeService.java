package com.filmae.service;

import com.filmae.client.AuthServiceClient;
import com.filmae.client.dto.AuthLoginRequest;
import com.filmae.client.dto.AuthRefreshRequest;
import com.filmae.client.dto.AuthRegisterRequest;
import com.filmae.client.dto.AuthTokenResponse;
import com.filmae.dto.auth.AuthSessionResponse;
import com.filmae.dto.auth.AuthSessionResponse.NextScreen;
import com.filmae.dto.auth.AuthUserDto;
import com.filmae.dto.auth.GoogleLoginRequest;
import com.filmae.dto.auth.LoginRequest;
import com.filmae.dto.auth.RefreshSessionRequest;
import com.filmae.dto.auth.RefreshSessionResponse;
import com.filmae.dto.auth.RegisterRequest;
import com.filmae.entity.User;
import com.filmae.exception.ApiException;
import com.filmae.security.AuthenticatedUser;
import com.filmae.security.JwtProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Fachada das rotas {@code /auth/*}: repassa ao auth-microservice e adapta ao contrato (envelope,
 * {@code expiresIn} em segundos, usuário local e {@code nextScreen}). Não abre transação própria para
 * não segurar conexão do banco durante as chamadas HTTP.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthFacadeService {

    /** TTL padrão do access token no auth (900000 ms), usado se ele não informar {@code expiresIn}. */
    static final long DEFAULT_EXPIRES_IN_SECONDS = 900;

    private final AuthServiceClient authServiceClient;
    private final JwtProvider jwtProvider;
    private final CurrentUserService currentUserService;

    /** O register do auth não devolve tokens, então o login é feito logo em seguida. */
    public AuthSessionResponse register(RegisterRequest request) {
        authServiceClient.register(new AuthRegisterRequest(request.name().trim(), request.email().trim(), request.password()));
        AuthTokenResponse tokens = authServiceClient.login(new AuthLoginRequest(request.email().trim(), request.password()));
        User user = syncLocalUser(tokens.accessToken());
        return new AuthSessionResponse(AuthUserDto.from(user), tokens.accessToken(), tokens.refreshToken(),
            toSeconds(tokens.expiresIn()), null);
    }

    public AuthSessionResponse login(LoginRequest request) {
        AuthTokenResponse tokens = authServiceClient.login(new AuthLoginRequest(request.email().trim(), request.password()));
        User user = syncLocalUser(tokens.accessToken());
        return new AuthSessionResponse(AuthUserDto.from(user), tokens.accessToken(), tokens.refreshToken(),
            toSeconds(tokens.expiresIn()), NextScreen.forOnboardingCompleted(user.isOnboardingCompleted()));
    }

    public RefreshSessionResponse refresh(RefreshSessionRequest request) {
        AuthTokenResponse tokens = authServiceClient.refresh(new AuthRefreshRequest(request.refreshToken()));
        return new RefreshSessionResponse(tokens.accessToken(), tokens.refreshToken(), toSeconds(tokens.expiresIn()));
    }

    public void logout(AuthenticatedUser user) {
        authServiceClient.logout(user.token());
    }

    /** O auth-microservice ainda não suporta login social; sempre 503 até existir esse endpoint lá. */
    public AuthSessionResponse loginWithGoogle(GoogleLoginRequest request) {
        throw ApiException.dependencyUnavailable("Login com Google ainda não está disponível", null);
    }

    /** Converte o {@code expiresIn} do auth (milissegundos) para segundos, arredondando para baixo. */
    static long toSeconds(Long expiresInMs) {
        if (expiresInMs == null || expiresInMs < 0) {
            return DEFAULT_EXPIRES_IN_SECONDS;
        }
        return expiresInMs / 1000;
    }

    /** Lê {@code sub}, {@code email} e {@code name} do access token emitido pelo auth e cria/atualiza o usuário local. */
    private User syncLocalUser(String accessToken) {
        AuthenticatedUser claims = jwtProvider.parseAccessToken(accessToken).orElseThrow(() -> {
            log.error("Access token do auth rejeitado pelo JwtProvider (JWT_SECRET ou issuer diferentes?)");
            return ApiException.dependencyUnavailable("Não foi possível validar a sessão emitida pelo serviço de autenticação", null);
        });
        return currentUserService.findOrCreate(claims.authUserId(), claims.email(), claims.name());
    }
}
