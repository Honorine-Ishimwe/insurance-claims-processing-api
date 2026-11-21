package com.claims.api.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;

@Configuration
public class AppConfig {

    /** Injected wherever "today" matters so tests can pin the date. */
    @Bean
    Clock clock() {
        return Clock.systemDefaultZone();
    }

    @Bean
    OpenAPI claimsOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Insurance Claims Processing API")
                        .version("v1")
                        .description("Submit, adjudicate and pay insurance claims. "
                                + "Log in via /api/v1/auth/login, then click Authorize and paste the token."))
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")));
    }
}
