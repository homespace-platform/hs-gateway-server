package com.hs.gateway.filter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import com.hs.gateway.advice.entity.enums.ErrorCode;
import com.hs.gateway.dto.GatewayErrorResponseWriter;

import reactor.core.publisher.Mono;

@Component
@Order(Ordered.LOWEST_PRECEDENCE)
@RequiredArgsConstructor
@Slf4j
public class GatewayErrorResponseFilter implements WebFilter {

    private final GatewayErrorResponseWriter errorResponseWriter;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        return chain.filter(exchange)
                .then(Mono.defer(() -> errorResponseWriter.writeIfErrorResponseIsEmpty(exchange)))
                .onErrorResume(exception -> {
                    log.error("Unhandled gateway exception", exception);
                    return errorResponseWriter.write(exchange, ErrorCode.UNCATEGORIZED_EXCEPTION);
                });
    }
}
