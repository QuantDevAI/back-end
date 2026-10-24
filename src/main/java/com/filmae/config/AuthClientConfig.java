package com.filmae.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.filmae.client.AuthServiceClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class AuthClientConfig {

    @Bean
    public AuthServiceClient authServiceClient(RestClient.Builder builder, AuthServiceProperties properties,
                                               ObjectMapper objectMapper) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());

        RestClient restClient = builder.clone()
            .baseUrl(properties.baseUrl())
            .requestFactory(requestFactory)
            .build();
        return new AuthServiceClient(restClient, objectMapper);
    }
}
