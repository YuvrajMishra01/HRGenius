package com.hrgenius.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger/OpenAPI metadata. Once JWT auth lands (Phase 1) the bearer scheme
 * below lets you authenticate from the Swagger UI.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI hrgeniusOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("HRGenius API")
                        .version("v1")
                        .description("HRGenius HRMS REST API — base path /api/v1"));
    }
}
