package com.filmae.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HealthResponse {

    private String status;
    private String service;
    private String version;
    private String environment;
    private Instant timestamp;
}
