package com.filmae.dto.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiFieldError {

    private String field;
    private String message;

    public static ApiFieldError of(String field, String message) {
        return new ApiFieldError(field, message);
    }
}
