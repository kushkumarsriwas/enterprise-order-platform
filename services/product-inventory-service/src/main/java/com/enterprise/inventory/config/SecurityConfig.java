package com.enterprise.inventory.config;

import com.enterprise.inventory.security.InternalApiKeyFilter;
import com.enterprise.inventory.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * URL rules (defense in depth - controllers also use @PreAuthorize):
 * the marketplace catalogue is public, Seller Central needs SELLER, the admin console needs ADMIN,
 * service-to-service /internal endpoints need the shared service key (and are not routed by the gateway).
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtFilter,
                                                   InternalApiKeyFilter internalApiKeyFilter,
                                                   AuthenticationEntryPoint entryPoint,
                                                   AccessDeniedHandler accessDeniedHandler) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(entryPoint).accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/api/products", "/api/products/**", "/api/categories").permitAll()
                        .requestMatchers("/api/seller/**", "/api/inventory/**").hasRole("SELLER")
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/internal/**").authenticated()
                        .requestMatchers("/", "/actuator/health/**", "/actuator/info",
                                "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/error").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(internalApiKeyFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(jwtFilter, InternalApiKeyFilter.class);
        return http.build();
    }
}
