package com.fit.iuh.gateway_server.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fit.iuh.gateway_server.constant.base.ErrorCode;
import com.fit.iuh.gateway_server.dto.ApiResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import reactor.core.publisher.Mono;

@Component
@Order(Ordered.LOWEST_PRECEDENCE)
@RequiredArgsConstructor
@Slf4j
public class GatewayErrorResponseFilter implements WebFilter {

    private final ObjectMapper objectMapper;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        return chain.filter(exchange)
                .then(Mono.defer(() -> writeBodyIfEmptyError(exchange)))
                .onErrorResume(exception -> {
                    log.error("Unhandled gateway exception", exception);
                    return writeErrorResponse(exchange, ErrorCode.UNCATEGORIZED_EXCEPTION);
                });
    }

    private Mono<Void> writeBodyIfEmptyError(ServerWebExchange exchange) {
        var response = exchange.getResponse();
        HttpStatusCode statusCode = response.getStatusCode();

        if (response.isCommitted() || statusCode == null || !statusCode.isError()) {
            return Mono.empty();
        }

        return writeErrorResponse(exchange, resolveErrorCode(statusCode));
    }

    private ErrorCode resolveErrorCode(HttpStatusCode statusCode) {
        if (HttpStatus.BAD_REQUEST.equals(statusCode)) {
            return ErrorCode.BAD_REQUEST;
        }
        if (HttpStatus.UNAUTHORIZED.equals(statusCode)) {
            return ErrorCode.UNAUTHENTICATED;
        }
        if (HttpStatus.FORBIDDEN.equals(statusCode)) {
            return ErrorCode.UNAUTHORIZED;
        }
        if (HttpStatus.NOT_FOUND.equals(statusCode)) {
            return ErrorCode.ROUTE_NOT_FOUND;
        }
        if (HttpStatus.METHOD_NOT_ALLOWED.equals(statusCode)) {
            return ErrorCode.METHOD_NOT_ALLOWED;
        }
        if (HttpStatus.TOO_MANY_REQUESTS.equals(statusCode)) {
            return ErrorCode.TOO_MANY_REQUESTS;
        }
        if (HttpStatus.SERVICE_UNAVAILABLE.equals(statusCode)) {
            return ErrorCode.SERVICE_UNAVAILABLE;
        }
        if (HttpStatus.GATEWAY_TIMEOUT.equals(statusCode)) {
            return ErrorCode.GATEWAY_TIMEOUT;
        }
        return ErrorCode.UNCATEGORIZED_EXCEPTION;
    }

    private Mono<Void> writeErrorResponse(ServerWebExchange exchange, ErrorCode errorCode) {
        var response = exchange.getResponse();

        if (response.isCommitted()) {
            return Mono.empty();
        }

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
