package com.filmae.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    public static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI filmaeOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("Filmaê API")
                .version("2.0.0")
                .description("API principal do Filmaê: autenticação (fachada do auth-microservice), onboarding, "
                    + "catálogo, coleção, avaliações, home, dashboard, notificações e perfil. "
                    + "Respostas usam o envelope {data, meta} e erros {error}."))
            .components(new Components()
                .addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")))
            .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
