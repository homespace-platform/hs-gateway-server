package com.hs.gateway.config.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hs.gateway.config.CorsConfig;
import com.hs.gateway.dto.GatewayErrorResponseWriter;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.*;
import org.springframework.core.env.MapPropertySource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.server.WebFilter;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;

class PaymentReceiptCorsTest {
    private static final String ORIGIN = "http://localhost:3000";
    private static final String PATH = "/api/v1/payment-receipts/receipt/pdf?download=false";

    @Configuration
    @EnableWebFluxSecurity
    @Import({SecurityConfig.class, CorsConfig.class})
    static class Config {
        @Bean ReactiveJwtDecoder jwtDecoder() {
            return token -> "receipt-test".equals(token)
                    ? Mono.just(Jwt.withTokenValue(token).header("alg", "RS256").subject("tenant")
                            .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build())
                    : Mono.error(new BadJwtException("Invalid test token"));
        }
        @Bean GatewayErrorResponseWriter errors() {
            return new GatewayErrorResponseWriter(new ObjectMapper());
        }
    }

    @Test void pdfAndAuthenticationErrorsKeepCorsHeadersWithoutMakingReceiptsPublic() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test",
                    Map.of("security.cors.allowed-origins", ORIGIN)));
            context.register(Config.class);
            context.refresh();
            byte[] pdf = new byte[480_569];
            System.arraycopy("%PDF-".getBytes(java.nio.charset.StandardCharsets.US_ASCII), 0, pdf, 0, 5);
            var client = WebTestClient.bindToWebHandler(exchange -> {
                        var response = exchange.getResponse();
                        response.getHeaders().setContentType(MediaType.APPLICATION_PDF);
                        response.getHeaders().set(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=receipt.pdf");
                        return response.writeWith(Mono.just(response.bufferFactory().wrap(pdf)));
                    })
                    .webFilter(context.getBeanProvider(WebFilter.class).orderedStream().toArray(WebFilter[]::new))
                    .configureClient().baseUrl("http://localhost:8080")
                    .codecs(c -> c.defaultCodecs().maxInMemorySize(1024 * 1024)).build();

            client.get().uri(PATH).header(HttpHeaders.ORIGIN, ORIGIN).exchange()
                    .expectStatus().isUnauthorized()
                    .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGIN)
                    .expectBody().jsonPath("$.code").isEqualTo(1002);
            client.get().uri(PATH).header(HttpHeaders.ORIGIN, ORIGIN).headers(h -> h.setBearerAuth("expired-test"))
                    .exchange().expectStatus().isUnauthorized()
                    .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGIN);
            client.get().uri(PATH).header(HttpHeaders.ORIGIN, ORIGIN).headers(h -> h.setBearerAuth("receipt-test"))
                    .exchange().expectStatus().isOk()
                    .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGIN)
                    .expectHeader().contentType(MediaType.APPLICATION_PDF)
                    .expectBody().consumeWith(result -> org.junit.jupiter.api.Assertions.assertArrayEquals(pdf, result.getResponseBody()));
            client.options().uri(PATH).header(HttpHeaders.ORIGIN, ORIGIN)
                    .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                    .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization")
                    .exchange().expectStatus().isOk()
                    .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGIN);
            client.get().uri(PATH).header(HttpHeaders.ORIGIN, "https://untrusted.example")
                    .headers(h -> h.setBearerAuth("receipt-test")).exchange()
                    .expectStatus().isForbidden().expectHeader().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN);
        }
    }
}
