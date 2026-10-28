package com.hs.gateway.config.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hs.gateway.dto.GatewayErrorResponseWriter;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.WebFilterChainProxy;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

class GuestSecurityTest {
    @Configuration
    @EnableWebFluxSecurity
    @Import(SecurityConfig.class)
    static class TestConfiguration {
        @Bean
        ReactiveJwtDecoder jwtDecoder() {
            return token -> Mono.error(new JwtException("No test JWT accepted"));
        }

        @Bean
        GatewayErrorResponseWriter errors() {
            return new GatewayErrorResponseWriter(new ObjectMapper());
        }
    }

    @Test
    void onlyGuestAiAndPublicListingsAreAnonymousNotAccountHistoryOrAdmin() {
        try (var context = new AnnotationConfigApplicationContext(TestConfiguration.class)) {
            var client = WebTestClient.bindToWebHandler(exchange -> exchange.getResponse().setComplete())
                    .webFilter(context.getBean(WebFilterChainProxy.class)).build();
            client.get().uri("/api/v1/ai/guest/agent/conversations").exchange().expectStatus().isOk();
            client.post().uri("/api/v1/ai/guest/agent/ask").exchange().expectStatus().isOk();
            client.post().uri("/api/v1/public/listings/ai/search").exchange().expectStatus().isOk();
            client.post().uri("/api/v1/public/listings/ai/nearby-summary").exchange().expectStatus().isOk();
            client.get().uri("/api/v1/public/listings/room/nearby-places").exchange().expectStatus().isOk();
            client.get().uri("/api/v1/public/listings/ai/nearby-places/search?keyword=school").exchange().expectStatus().isOk();
            client.get().uri("/api/v1/admin/nearby-scans").exchange().expectStatus().isUnauthorized();
            client.post().uri("/api/v1/admin/listings/room/nearby-scans").exchange().expectStatus().isUnauthorized();
            client.get().uri("/api/v1/ai/agent/conversations").exchange().expectStatus().isUnauthorized();
            client.post().uri("/api/v1/ai/agent/ask").exchange().expectStatus().isUnauthorized();
            client.get().uri("/api/v1/admin/ai/knowledge/documents").exchange().expectStatus().isUnauthorized();
        }
    }
}
