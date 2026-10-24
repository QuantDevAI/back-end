package com.filmae.controller;

import com.filmae.dto.common.ApiResponse;
import com.filmae.dto.home.HomeResponse;
import com.filmae.entity.User;
import com.filmae.security.CurrentUser;
import com.filmae.service.HomeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Home")
@RequiredArgsConstructor
public class HomeController {

    private final HomeService homeService;

    @Operation(summary = "Tela inicial: filme do dia, recomendações, fora da bolha e contadores")
    @GetMapping("/home")
    public ApiResponse<HomeResponse> home(@CurrentUser User user) {
        return ApiResponse.of(homeService.home(user));
    }
}
