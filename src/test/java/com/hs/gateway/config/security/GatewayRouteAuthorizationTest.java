package com.hs.gateway.config.security;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import static org.junit.jupiter.api.Assertions.*;

class GatewayRouteAuthorizationTest {
    @Test
    void adminPathsAreProtectedEvenWithRepeatedOrAliasedApiPrefixes() {
        var authorization = new GatewayRouteAuthorization();
        for (String path : new String[]{"/api/v1/admin/users", "/api/v1/api/v1/admin/users",
                "/api/v1/api/v2/ADMIN/users", "/api/v1//admin/users", "/api/v1/admin;x=1/users",
                "/api/v1/api/v1/notifications/admin/broadcast"}) {
            var exchange = MockServerWebExchange.from(MockServerHttpRequest.get(path));
            assertFalse(authorization.isAllowed(exchange, "USER"), path);
            assertTrue(authorization.isAllowed(exchange, "ADMIN"), path);
        }
        assertTrue(authorization.isAllowed(
                MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/listings/admin-tips")), "USER"));
    }

    @Test
    void blocksInternalAndDotSegmentPaths() {
        var authorization = new GatewayRouteAuthorization();
        for (String path : new String[]{"/api/v1/internal/users/1/permissions", "/api/v1/api/v1/internal/x",
                "/api/v1/./admin/users", "/api/v1/a/../admin"}) {
            assertTrue(authorization.isBlockedPath(MockServerWebExchange.from(MockServerHttpRequest.get(path))), path);
        }
        assertFalse(authorization.isBlockedPath(
                MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/users/me"))));
    }

    @Test
    void broadcastRequiresAdminWhileFeedRemainsAvailableToUsers() {
        var authorization = new GatewayRouteAuthorization();
        var broadcast = MockServerWebExchange.from(MockServerHttpRequest.post("/api/v1/notifications/admin/broadcast"));
        assertFalse(authorization.isAllowed(broadcast, "USER"));
        assertFalse(authorization.isAllowed(broadcast, null));
        assertTrue(authorization.isAllowed(broadcast, "ADMIN"));
        var rewritten = MockServerWebExchange.from(MockServerHttpRequest.post("/notifications/admin/broadcast"));
        rewritten.getAttributes().put(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR,
                Route.async().id("notification-broadcast-route").uri("lb://hs-core-api").predicate(e -> true).build());
        assertFalse(authorization.isAllowed(rewritten, "USER"));
        assertTrue(authorization.isAllowed(rewritten, "ADMIN"));
        var feed = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/notifications/feed"));
        assertTrue(authorization.isAllowed(feed, "USER"));
    }
}
