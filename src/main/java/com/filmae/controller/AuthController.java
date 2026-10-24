package com.filmae.controller;

import com.filmae.dto.auth.AuthSessionResponse;
import com.filmae.dto.auth.GoogleLoginRequest;
import com.filmae.dto.auth.LoginRequest;
import com.filmae.dto.auth.RefreshSessionRequest;
import com.filmae.dto.auth.RefreshSessionResponse;
import com.filmae.dto.auth.RegisterRequest;
import com.filmae.dto.common.ApiResponse;
import com.filmae.security.AuthenticatedUser;
import com.filmae.security.CurrentUser;
import com.filmae.service.AuthFacadeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Auth")
public class AuthController {

    private final AuthFacadeService authFacadeService;

    @Operation(summary = "Cria a conta e inicia a sessão")
    @SecurityRequirements
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthSessionResponse>> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.created(authFacadeService.register(request));
    }

    @Operation(summary = "Login com e-mail e senha")
    @SecurityRequirements
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthSessionResponse>> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authFacadeService.login(request));
    }

    @Operation(summary = "Login com Google (indisponível: responde 503)")
    @SecurityRequirements
    @PostMapping("/google")
    public ResponseEntity<ApiResponse<AuthSessionResponse>> google(@RequestBody(required = false) GoogleLoginRequest request) {
        return ApiResponse.ok(authFacadeService.loginWithGoogle(request));
    }

    @Operation(summary = "Renova a sessão com o refresh token")
    @SecurityRequirements
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<RefreshSessionResponse>> refresh(@Valid @RequestBody RefreshSessionRequest request) {
        return ApiResponse.ok(authFacadeService.refresh(request));
    }

    @Operation(summary = "Encerra a sessão (revoga os refresh tokens no auth)")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@CurrentUser AuthenticatedUser user) {
        authFacadeService.logout(user);
        return ResponseEntity.noContent().build();
    }
}
