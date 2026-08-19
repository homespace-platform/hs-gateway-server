package com.hs.gateway.filter;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import com.hs.gateway.config.security.GatewayRouteAuthorization;
import com.hs.gateway.config.security.UserAccessResolver;
import com.hs.gateway.advice.entity.enums.ErrorCode;
import com.hs.gateway.dto.GatewayErrorResponseWriter;

import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class UserHeaderFilter implements GlobalFilter {

    private final UserAccessResolver userAccessResolver;
    private final GatewayRouteAuthorization routeAuthorization;
    private final GatewayErrorResponseWriter errorResponseWriter;

    /**
     * Filter chính của Gateway.
     * Nếu request có JWT hợp lệ thì lấy userId/email, xác định role, kiểm tra quyền truy cập
     * rồi gắn thông tin user vào header trước khi chuyển request xuống service phía sau.
     */
    @NullMarked
    @Override
    public Mono<Void> filter(
            ServerWebExchange exchange,
            GatewayFilterChain chain
    ) {
        return exchange.getPrincipal()
                .filter(p -> p instanceof JwtAuthenticationToken)
                .cast(JwtAuthenticationToken.class)
                .flatMap(jwtAuth -> {
                    String userId = jwtAuth.getToken().getSubject();
                    String email = jwtAuth.getToken().getClaim("email");

                    return userAccessResolver.resolve(jwtAuth)
                            .flatMap(access -> {
                                if (!routeAuthorization.isAllowed(exchange, access.role())) {
                                    return errorResponseWriter.write(exchange, ErrorCode.UNAUTHORIZED);
                                }

                                ServerHttpRequest mutatedRequest =
                                        mutateRequest(
                                                exchange,
                                                userId,
                                                email,
                                                access.role(),
                                                access.authorities()
                                        );
                                return chain.filter(exchange.mutate().request(mutatedRequest).build());
                            });
                })
                .switchIfEmpty(chain.filter(exchange));
    }

    /**
     * Gắn thông tin người dùng đã xác thực vào request trước khi chuyển tiếp xuống service phía sau.
     */
    private ServerHttpRequest mutateRequest(
            ServerWebExchange exchange, String userId,
            String email, String role, String authorities
    ) {
        return exchange.getRequest().mutate()
                .header("X-User-Id", userId)
                .header("X-User-Email", email)
                .header("X-User-Role", role)
                .header("X-User-Authorities", authorities)
                .build();
    }

}
