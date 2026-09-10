package com.hs.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.oauth2.jwt.Jwt;

class UserHeaderFilterTest {

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
