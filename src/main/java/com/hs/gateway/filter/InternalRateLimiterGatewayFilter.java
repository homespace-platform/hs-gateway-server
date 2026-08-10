package com.hs.gateway.filter;

import lombok.RequiredArgsConstructor;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.stereotype.Component;

import com.hs.gateway.constant.base.ErrorCode;
import com.hs.gateway.dto.GatewayErrorResponseWriter;

@Component
@RequiredArgsConstructor
public class InternalRateLimiterGatewayFilter {

    private final GatewayErrorResponseWriter errorResponseWriter;

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

                    return errorResponseWriter.write(exchange, ErrorCode.TOO_MANY_REQUESTS);
                });
    }
}
