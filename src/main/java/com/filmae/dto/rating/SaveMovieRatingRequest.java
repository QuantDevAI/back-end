package com.filmae.dto.rating;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SaveMovieRatingRequest {

    @NotNull(message = "A nota é obrigatória")
    private BigDecimal rating;
}
