package com.filmae.controller;

import com.filmae.dto.HealthResponse;
import com.filmae.dto.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@Tag(name = "Health")
public class HealthController {

    @Value("${info.app.name:filmae-back-end}")
    private String appName;

    @Value("${info.app.version:1.0.0}")
    private String appVersion;

    @Value("${info.app.environment:default}")
    private String environment;

    @Operation(summary = "Status do serviço")
    @SecurityRequirements
    @GetMapping({"/health", "/api/v1/health"})
    public ApiResponse<HealthResponse> health() {
        return ApiResponse.of(HealthResponse.builder()
            .status("UP")
            .service(appName)
            .version(appVersion)
            .environment(environment)
            .timestamp(Instant.now())
            .build());
    }
}
