package com.hs.gateway.config.security;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;

@Component
public class GatewayRouteAuthorization {

    private static final String ADMIN_ROLE = "ADMIN";

    public boolean isAllowed(ServerWebExchange exchange, String role) {
        Route matchedRoute = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        if (paths(exchange).stream().anyMatch(GatewayRouteAuthorization::isAdminPath)
                || (matchedRoute != null && ("ai-service-admin-route".equals(matchedRoute.getId())
                    || "notification-broadcast-route".equals(matchedRoute.getId())))) {
            return ADMIN_ROLE.equals(role);
        }

        return true;
    }

    /** Core's /internal/** is service-to-service only; dot segments could smuggle a path past prefix checks. */
    public boolean isBlockedPath(ServerWebExchange exchange) {
        return paths(exchange).stream().anyMatch(path -> {
            List<String> segments = segments(path);
            List<String> rest = stripApiPrefixes(segments);
            return segments.stream().anyMatch(s -> s.equals(".") || s.equals(".."))
                    || (!rest.isEmpty() && rest.get(0).equalsIgnoreCase("internal"));
        });
    }

    /** Admin when the first segment after any number of /api/vN prefixes is "admin" (core aliases /api/v1/admin). */
    static boolean isAdminPath(String path) {
        List<String> rest = stripApiPrefixes(segments(path));
        return (!rest.isEmpty() && rest.get(0).equalsIgnoreCase("admin"))
                || (rest.size() >= 2 && rest.get(0).equalsIgnoreCase("notifications") && rest.get(1).equalsIgnoreCase("admin"));
    }

    /** Current path plus the pre-rewrite URLs, both decoded. */
    private static List<String> paths(ServerWebExchange exchange) {
        List<String> paths = new ArrayList<>();
        paths.add(exchange.getRequest().getURI().getPath());
        Set<URI> originals = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ORIGINAL_REQUEST_URL_ATTR);
        if (originals != null) originals.forEach(uri -> paths.add(uri.getPath()));
        return paths;
    }

    private static List<String> segments(String path) {
        List<String> segments = new ArrayList<>();
        if (path == null) return segments;
        for (String raw : path.split("/")) {
            int params = raw.indexOf(';');
            String segment = params >= 0 ? raw.substring(0, params) : raw;
            if (!segment.isEmpty()) segments.add(segment);
        }
        return segments;
    }

    private static List<String> stripApiPrefixes(List<String> segments) {
        int i = 0;
        while (i + 1 < segments.size() && segments.get(i).equalsIgnoreCase("api")
                && segments.get(i + 1).matches("(?i)v\\d+")) {
            i += 2;
        }
        return segments.subList(i, segments.size());
    }
}
