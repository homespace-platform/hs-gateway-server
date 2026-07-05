package com.fit.iuh.gateway_server.config.security;

import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

@Component
public class GatewayRouteAuthorization {

    private static final String ADMIN_ROLE = "ADMIN";
    private static final String ADMIN_ROUTE_PATTERN = "^/api/v\\d+/admin(/.*)?$";

    public boolean isAllowed(ServerWebExchange exchange, String role) {
        String path = exchange.getRequest().getPath().pathWithinApplication().value();

        if (path.matches(ADMIN_ROUTE_PATTERN)) {
            return ADMIN_ROLE.equals(role);
        }

        return true;
    }
}
