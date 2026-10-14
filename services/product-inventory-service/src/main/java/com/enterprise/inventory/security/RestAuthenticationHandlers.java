package com.enterprise.inventory.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

/** JSON 401/403 responses instead of Spring's default HTML/empty bodies. */
@Configuration
public class RestAuthenticationHandlers {

    @Bean
    public AuthenticationEntryPoint restAuthenticationEntryPoint() {
        return (request, response, ex) -> JsonErrorWriter.write(request, response, 401, "Unauthorized",
                "Missing, invalid or expired access token");
    }

    @Bean
    public AccessDeniedHandler restAccessDeniedHandler() {
        return (request, response, ex) -> JsonErrorWriter.write(request, response, 403, "Forbidden",
                "You do not have permission to perform this action");
    }
}
