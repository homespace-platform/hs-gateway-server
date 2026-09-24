package com.hs.gateway.config.security;

import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;

@Component
public class GatewayRouteAuthorization {

    private static final String ADMIN_ROLE = "ADMIN";
    private static final String ADMIN_ROUTE_PATTERN = "^/api/v\\d+/admin(/.*)?$";

    public boolean isAllowed(ServerWebExchange exchange, String role) {
        String path = exchange.getRequest().getPath().pathWithinApplication().value();

        Route matchedRoute = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        if (path.matches(ADMIN_ROUTE_PATTERN)
                || (matchedRoute != null && "ai-service-admin-route".equals(matchedRoute.getId()))) {
            return ADMIN_ROLE.equals(role);
        }

        return true;
    }
}
