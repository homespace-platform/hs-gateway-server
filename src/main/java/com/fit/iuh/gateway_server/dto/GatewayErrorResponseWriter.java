package com.fit.iuh.gateway_server.dto;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fit.iuh.gateway_server.constant.base.ErrorCode;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class GatewayErrorResponseWriter {

    private final ObjectMapper objectMapper;

    public Mono<Void> write(ServerWebExchange exchange, ErrorCode errorCode) {
        var response = exchange.getResponse();

        if (response.isCommitted()) {
            return Mono.empty();
        }

        response.setStatusCode(errorCode.getStatusCode());
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

    public Mono<Void> writeIfErrorResponseIsEmpty(ServerWebExchange exchange) {
        var response = exchange.getResponse();
        HttpStatusCode statusCode = response.getStatusCode();

        if (response.isCommitted() || statusCode == null || !statusCode.isError()) {
            return Mono.empty();
        }

        return write(exchange, resolveErrorCode(statusCode));
    }

    private ErrorCode resolveErrorCode(HttpStatusCode statusCode) {
        return switch (statusCode.value()) {
            case 400 -> ErrorCode.BAD_REQUEST;
            case 401 -> ErrorCode.UNAUTHENTICATED;
            case 403 -> ErrorCode.UNAUTHORIZED;
            case 404 -> ErrorCode.ROUTE_NOT_FOUND;
            case 405 -> ErrorCode.METHOD_NOT_ALLOWED;
            case 429 -> ErrorCode.TOO_MANY_REQUESTS;
            case 503 -> ErrorCode.SERVICE_UNAVAILABLE;
            case 504 -> ErrorCode.GATEWAY_TIMEOUT;
            default -> ErrorCode.UNCATEGORIZED_EXCEPTION;
        };
    }
}
