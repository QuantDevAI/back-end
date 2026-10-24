package com.filmae.controller;

import com.filmae.dto.common.ApiResponse;
import com.filmae.dto.dashboard.DashboardResponse;
import com.filmae.security.CurrentUser;
import com.filmae.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me/dashboard")
@Tag(name = "Dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @Operation(summary = "KPIs, gêneros mais assistidos e prévia do histórico")
    @GetMapping
    public ApiResponse<DashboardResponse> getDashboard(@CurrentUser Long userId) {
        return ApiResponse.of(dashboardService.getDashboard(userId));
    }
}
