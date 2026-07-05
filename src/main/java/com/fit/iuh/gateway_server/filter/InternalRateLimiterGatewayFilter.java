package com.fit.iuh.gateway_server.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fit.iuh.gateway_server.constant.base.ErrorCode;
import com.fit.iuh.gateway_server.dto.ApiResponse;

import lombok.RequiredArgsConstructor;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class InternalRateLimiterGatewayFilter {

    private final ObjectMapper objectMapper;

    public GatewayFilter apply(
            String routeId,
            RedisRateLimiter rateLimiter,
            KeyResolver keyResolver
    ) {
        return (exchange, chain) -> keyResolver.resolve(exchange)
                .defaultIfEmpty("unknown")
                .flatMap(key -> rateLimiter.isAllowed(routeId, key))
                .flatMap(response -> {
                    response.getHeaders().forEach((headerName, headerValue) ->
                            exchange.getResponse().getHeaders().add(headerName, headerValue));

                    if (response.isAllowed()) {
                        return chain.filter(exchange);
                    }

                    return writeErrorResponse(exchange, ErrorCode.TOO_MANY_REQUESTS);
                });
    }

    private Mono<Void> writeErrorResponse(ServerWebExchange exchange, ErrorCode errorCode) {
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
