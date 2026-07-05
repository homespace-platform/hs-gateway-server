package com.fit.iuh.gateway_server.filter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullMarked;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import com.fit.iuh.gateway_server.client.UserInternalClient;
import com.fit.iuh.gateway_server.dto.UserAccess;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserHeaderFilter implements GlobalFilter {

    private final ObjectProvider<@NonNull UserInternalClient> userInternalClientProvider;

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

                    String jwtRole = resolveRoleFromJwt(jwtAuth);

                    return fetchUserAccess(userId, jwtRole)
                            .flatMap(access -> {
                                if (!isAllowed(exchange, access.role())) {
                                    exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
                                    return exchange.getResponse().setComplete();
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
     * Lấy role và permission mới nhất từ User Service bằng Feign Client.
     * Nếu User Service lỗi hoặc chưa trả dữ liệu thì dùng role lấy từ JWT để request vẫn tiếp tục.
     * Sử dụng boundedElastic vì Feign là blocking call, không nên chạy trực tiếp trên reactive thread.
     */
    private Mono<@NonNull UserAccess> fetchUserAccess(String userId, String fallbackRole) {
        return Mono.fromCallable(() -> {
                    UserInternalClient client = userInternalClientProvider.getIfAvailable();
                    return (client != null) ? client.getUserPermissions(userId) : null;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .map(response -> {
                    if (response != null && response.getData() != null) {
                        String role = normalizeRole(response.getData().role());
                        if (role.isBlank()) {
                            role = fallbackRole;
                        }
                        String authorities = response.getData().permissions() != null
                                ? String.join(",", response.getData().permissions())
                                : "";
                        return new UserAccess(role, authorities);
                    }
                    return new UserAccess(fallbackRole, "");
                })
                .defaultIfEmpty(new UserAccess(fallbackRole, ""))
                .onErrorResume(ex -> {
                    log.warn(
                            "Could not fetch user access for userId {}. Falling back to JWT role '{}'. Reason: {}",
                            userId,
                            fallbackRole,
                            ex.getMessage()
                    );
                    return Mono.just(new UserAccess(fallbackRole, ""));
                });
    }

    /**
     * Đọc role từ JWT do Keycloak cấp.
     * Ưu tiên realm_access trước, sau đó mới kiểm tra resource_access của từng client.
     */
    private String resolveRoleFromJwt(JwtAuthenticationToken jwtAuth) {
        List<String> rolePriority = List.of("ADMIN", "CUSTOMER");

        Object realmAccess = jwtAuth.getToken().getClaims().get("realm_access");
        if (realmAccess instanceof Map<?, ?> map) {
            String role = findFirstKnownRole(map.get("roles"), rolePriority);
            if (!role.isBlank()) {
                return role;
            }
        }

        Object resourceAccess = jwtAuth.getToken().getClaims().get("resource_access");
        if (resourceAccess instanceof Map<?, ?> clients) {
            for (Object clientAccess : clients.values()) {
                if (clientAccess instanceof Map<?, ?> map) {
                    String role = findFirstKnownRole(map.get("roles"), rolePriority);
                    if (!role.isBlank()) {
                        return role;
                    }
                }
            }
        }

        return "";
    }

    /**
     * Tìm role đầu tiên nằm trong danh sách role hệ thống đang hỗ trợ.
     * Role được normalize trước khi so sánh để tránh lệch do prefix ROLE_ hoặc chữ thường/chữ hoa.
     */
    private String findFirstKnownRole(Object rolesObject, List<String> rolePriority) {
        if (!(rolesObject instanceof Collection<?> roles)) {
            return "";
        }

        List<String> normalizedRoles = roles.stream()
                .filter(Objects::nonNull)
                .map(Object::toString)
                .map(this::normalizeRole)
                .toList();

        return rolePriority.stream()
                .filter(normalizedRoles::contains)
                .findFirst()
                .orElse("");
    }

    /**
     * Chuẩn hóa role về cùng một format để dễ so sánh.
     * Ví dụ: ROLE_ADMIN, admin đều được đưa về ADMIN.
     */
    private String normalizeRole(String role) {
        return role == null ? "" : role.replaceFirst("^ROLE_", "").toUpperCase();
    }

    /**
     * Kiểm tra request hiện tại có được phép đi tiếp theo role hay không.
     * Hiện tại dự án chỉ giới hạn các endpoint /admin cho ADMIN; các route khác cho đi tiếp.
     */
    private boolean isAllowed(ServerWebExchange exchange, String role) {
        String path = exchange.getRequest().getPath().pathWithinApplication().value();

        if (path.matches("^/[^/]+-service/admin(/.*)?$")) {
            return "ADMIN".equals(role);
        }

        return true;
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
