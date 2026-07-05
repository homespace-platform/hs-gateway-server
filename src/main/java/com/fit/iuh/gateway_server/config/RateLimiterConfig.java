package com.fit.iuh.gateway_server.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import reactor.core.publisher.Mono;

@Configuration
public class RateLimiterConfig {
    @Bean
    @Primary
    public KeyResolver ipKeyResolver() {
        return exchange -> Mono.just(
                exchange.getRequest().getRemoteAddress() != null
                        ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                        : "unknown_ip"
        );
    }

    @Bean
    public RedisRateLimiter defaultRateLimiter(
            @Value("${gateway.rate-limiter.replenish-rate:5}") int replenishRate,
            @Value("${gateway.rate-limiter.burst-capacity:10}") int burstCapacity,
            @Value("${gateway.rate-limiter.requested-tokens:1}") int requestedTokens) {
        return new RedisRateLimiter(replenishRate, burstCapacity, requestedTokens);
    }
}
