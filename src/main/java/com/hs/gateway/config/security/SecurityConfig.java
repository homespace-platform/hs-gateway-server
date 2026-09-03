package com.hs.gateway.config.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

import com.hs.gateway.advice.entity.enums.ErrorCode;
import com.hs.gateway.dto.GatewayErrorResponseWriter;

@Configuration
public class SecurityConfig {

    private static final String API_V1_PREFIX = "/api/v1";

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(
            ServerHttpSecurity http,
            GatewayErrorResponseWriter errorResponseWriter
    ) {
        http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .exceptionHandling(exceptionHandling -> exceptionHandling
                        .authenticationEntryPoint((exchange, exception) ->
                                errorResponseWriter.write(exchange, ErrorCode.UNAUTHENTICATED))
                        .accessDeniedHandler((exchange, exception) ->
                                errorResponseWriter.write(exchange, ErrorCode.UNAUTHORIZED)))
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .pathMatchers("/actuator/**").permitAll()
                        .pathMatchers("/fallback/**").permitAll()
                        // User Service
                        .pathMatchers(API_V1_PREFIX + "/actuator/prometheus").permitAll()
                        .pathMatchers(API_V1_PREFIX + "/public/**").permitAll()
                        .pathMatchers(HttpMethod.GET, API_V1_PREFIX + "/chat/ping").permitAll()
                        .pathMatchers(HttpMethod.GET, API_V1_PREFIX + "/news/ping").permitAll()
                        .pathMatchers(HttpMethod.GET, API_V1_PREFIX + "/listings/me").authenticated()
                        .pathMatchers(HttpMethod.GET, API_V1_PREFIX + "/listings/**").permitAll()
                        .pathMatchers(HttpMethod.GET, API_V1_PREFIX + "/storage/*/view-url").permitAll()
                        .pathMatchers(API_V1_PREFIX + "/internal/**").denyAll()
                        .pathMatchers(API_V1_PREFIX + "/**").authenticated()
                        // Add Service here ...
                        .anyExchange().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationEntryPoint((exchange, exception) ->
                                errorResponseWriter.write(exchange, ErrorCode.UNAUTHENTICATED))
                        .accessDeniedHandler((exchange, exception) ->
                                errorResponseWriter.write(exchange, ErrorCode.UNAUTHORIZED))
                        .jwt(Customizer.withDefaults()));
        return http.build();
    }
}
