package com.hs.gateway.config.security;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import static org.junit.jupiter.api.Assertions.*;

class GatewayRouteAuthorizationTest {
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
