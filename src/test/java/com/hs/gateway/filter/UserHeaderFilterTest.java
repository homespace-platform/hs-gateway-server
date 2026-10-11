package com.hs.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.test.util.ReflectionTestUtils;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;
import reactor.core.publisher.Mono;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.hs.gateway.config.security.UserAccessResolver;
import com.hs.gateway.config.security.GatewayRouteAuthorization;
import com.hs.gateway.dto.GatewayErrorResponseWriter;
import com.hs.gateway.dto.UserAccess;

class UserHeaderFilterTest {

    @Test
    void authenticatedAiRequestRunsOnceAndDoesNotFallThroughToAnonymousChain() {
        var resolver = mock(UserAccessResolver.class);
        var filter = new UserHeaderFilter(resolver, new GatewayRouteAuthorization(),
                mock(GatewayErrorResponseWriter.class));
        ReflectionTestUtils.setField(filter, "gatewayInternalSecret", "test-only-internal-gateway-secret-32");
        var jwt = Jwt.withTokenValue("test").header("alg", "none").subject("tenant-1")
                .claim("email", "tenant@example.test").claim("name", "Tenant").build();
        var auth = new JwtAuthenticationToken(jwt);
        when(resolver.resolve(auth)).thenReturn(Mono.just(new UserAccess("USER", "ROLE_USER")));
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/api/v1/ai/agent/ask"))
                .mutate().principal(Mono.just(auth)).build();
        exchange.getAttributes().put(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR,
                Route.async().id("ai-service-route").uri("http://localhost:8084").predicate(e -> true).build());
        var calls = new AtomicInteger();
        filter.filter(exchange, forwarded -> {
            calls.incrementAndGet();
            assertThat(forwarded.getRequest().getHeaders().getFirst("X-User-Id")).isEqualTo("tenant-1");
            assertThat(forwarded.getRequest().getHeaders().getFirst("X-User-Role")).isEqualTo("USER");
            assertThat(forwarded.getRequest().getHeaders().getFirst("X-Internal-Secret"))
                    .isEqualTo("test-only-internal-gateway-secret-32");
            return Mono.empty();
        }).block();
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void anonymousAiRequestGetsGatewayTrustWithoutAccountIdentityAndRunsOnce() {
        var filter = new UserHeaderFilter(mock(UserAccessResolver.class), new GatewayRouteAuthorization(),
                mock(GatewayErrorResponseWriter.class));
        ReflectionTestUtils.setField(filter, "gatewayInternalSecret", "test-only-internal-gateway-secret-32");
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/ai/guest/agent/conversations")
                .remoteAddress(new InetSocketAddress("192.0.2.10", 1234))
                .header("X-User-Id", "admin").header("X-User-Role", "ADMIN")
                .header("X-Ai-Client-IP", "forged").header("X-Internal-Secret", "forged"));
        exchange.getAttributes().put(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR,
                Route.async().id("ai-service-route").uri("http://localhost:8084").predicate(e -> true).build());
        var calls = new AtomicInteger();
        filter.filter(exchange, forwarded -> {
            calls.incrementAndGet();
            var headers = forwarded.getRequest().getHeaders();
            assertThat(headers.getFirst("X-User-Id")).isNull();
            assertThat(headers.getFirst("X-User-Role")).isNull();
            assertThat(headers.getFirst("X-Ai-Client-IP")).isEqualTo("192.0.2.10");
            assertThat(headers.getFirst("X-Internal-Secret")).isEqualTo("test-only-internal-gateway-secret-32");
            return Mono.empty();
        }).block();
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void stripsForgedUserHeaders() {
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/chat/socket.io")
                        .header("X-User-Id", "forged-user"));

        var sanitized = UserHeaderFilter.stripUserHeaders(exchange);

        assertThat(sanitized.getRequest().getHeaders().getFirst("X-User-Id")).isNull();
    }

    @Test
    void resolvesDisplayNameWithEmailFallback() {
        Jwt named = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("admin-1")
                .claim("email", "admin@homespace.vn")
                .claim("name", "Home Space Admin")
                .build();
        Jwt unnamed = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("admin-1")
                .claim("email", "admin@homespace.vn")
                .build();
        Jwt splitName = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("admin-1")
                .claim("email", "admin@homespace.vn")
                .claim("given_name", "Home")
                .claim("family_name", "Space")
                .build();

        assertThat(UserHeaderFilter.resolveDisplayName(named)).isEqualTo("Home Space Admin");
        assertThat(UserHeaderFilter.resolveDisplayName(splitName)).isEqualTo("Home Space");
        assertThat(UserHeaderFilter.resolveDisplayName(unnamed)).isEqualTo("admin@homespace.vn");
    }

    @Test
    void encodesDisplayNameAsUtf8Base64ForDownstreamHeaders() {
        assertThat(UserHeaderFilter.encodeDisplayName("Tuấn Đào"))
                .isEqualTo("VHXhuqVuIMSQw6Bv");
    }
}
