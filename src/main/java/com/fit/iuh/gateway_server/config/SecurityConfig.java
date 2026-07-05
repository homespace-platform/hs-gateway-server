package com.fit.iuh.gateway_server.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fit.iuh.gateway_server.constant.base.ErrorCode;
import com.fit.iuh.gateway_server.dto.ApiResponse;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http, ObjectMapper objectMapper) {
        http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .exceptionHandling(exceptionHandling -> exceptionHandling
                        .authenticationEntryPoint((exchange, exception) ->
                                writeErrorResponse(exchange, ErrorCode.UNAUTHENTICATED, objectMapper))
                        .accessDeniedHandler((exchange, exception) ->
                                writeErrorResponse(exchange, ErrorCode.UNAUTHORIZED, objectMapper)))
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .pathMatchers("/actuator/**").permitAll()
                        .pathMatchers("/fallback/**").permitAll()
                        // User Service
                        .pathMatchers("/user-service/actuator/prometheus").permitAll()
                        .pathMatchers("/user-service/public/**").permitAll()
                        .pathMatchers("/user-service/internal/**").denyAll()
                        .pathMatchers("/user-service/**").authenticated()
                        // Add Service here ...
                        .anyExchange().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationEntryPoint((exchange, exception) ->
                                writeErrorResponse(exchange, ErrorCode.UNAUTHENTICATED, objectMapper))
                        .accessDeniedHandler((exchange, exception) ->
                                writeErrorResponse(exchange, ErrorCode.UNAUTHORIZED, objectMapper))
                        .jwt(Customizer.withDefaults()));
        return http.build();
    }

    private Mono<Void> writeErrorResponse(
            ServerWebExchange exchange,
            ErrorCode errorCode,
            ObjectMapper objectMapper
    ) {
        var response = exchange.getResponse();
        response.setStatusCode(HttpStatus.valueOf(errorCode.getStatusCode().value()));
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        ApiResponse<Void> body = ApiResponse.<Void>builder()
                .code(errorCode.getCode())
                .message(errorCode.getMessage())
                .build();

        try {
            byte[] bytes = objectMapper.writeValueAsBytes(body);
            response.getHeaders().setContentLength(bytes.length);
            DataBuffer buffer = response.bufferFactory().wrap(bytes);
            return response.writeWith(Mono.just(buffer));
        } catch (JsonProcessingException exception) {
            return response.setComplete();
        }
    }
}
