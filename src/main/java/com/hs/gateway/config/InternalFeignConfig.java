package com.hs.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import feign.RequestInterceptor;

/** Proves to hs-core-api that Feign calls (e.g. /internal/users/{id}/permissions) come from the Gateway. */
@Configuration
public class InternalFeignConfig {

    @Bean
    RequestInterceptor gatewayInternalSecretInterceptor(@Value("${GATEWAY_INTERNAL_SECRET:}") String secret) {
        if (secret.isBlank()) throw new IllegalStateException("GATEWAY_INTERNAL_SECRET must not be blank");
        return template -> template.header("X-Internal-Secret", secret);
    }
}
