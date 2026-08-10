package com.hs.gateway.config.security;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.hs.gateway.client.UserInternalClient;
import com.hs.gateway.dto.UserAccess;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserAccessResolver {

    private static final List<String> ROLE_PRIORITY = List.of("ADMIN", "CUSTOMER");

    private final ObjectProvider<@NonNull UserInternalClient> userInternalClientProvider;

    public Mono<@NonNull UserAccess> resolve(JwtAuthenticationToken jwtAuth) {
        String userId = jwtAuth.getToken().getSubject();
        String fallbackRole = resolveRoleFromJwt(jwtAuth);

        return Mono.fromCallable(() -> {
                    UserInternalClient client = userInternalClientProvider.getIfAvailable();
                    return (client != null) ? client.getUserPermissions(userId) : null;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .map(response -> {
                    if (response != null && response.getResult() != null) {
                        String role = normalizeRole(response.getResult().role());
                        String authorities = response.getResult().permissions() != null
                                ? String.join(",", response.getResult().permissions())
                                : "";

                        return new UserAccess(role.isBlank() ? fallbackRole : role, authorities);
                    }

                    return new UserAccess(fallbackRole, "");
                })
                .defaultIfEmpty(new UserAccess(fallbackRole, ""))
                .onErrorResume(exception -> {
                    log.warn(
                            "Could not fetch user access for userId {}. Falling back to JWT role '{}'. Reason: {}",
                            userId,
                            fallbackRole,
                            exception.getMessage()
                    );
                    return Mono.just(new UserAccess(fallbackRole, ""));
                });
    }

    private String resolveRoleFromJwt(JwtAuthenticationToken jwtAuth) {
        Object realmAccess = jwtAuth.getToken().getClaims().get("realm_access");
        if (realmAccess instanceof Map<?, ?> map) {
            String role = findFirstKnownRole(map.get("roles"));
            if (!role.isBlank()) {
                return role;
            }
        }

        Object resourceAccess = jwtAuth.getToken().getClaims().get("resource_access");
        if (resourceAccess instanceof Map<?, ?> clients) {
            for (Object clientAccess : clients.values()) {
                if (clientAccess instanceof Map<?, ?> map) {
                    String role = findFirstKnownRole(map.get("roles"));
                    if (!role.isBlank()) {
                        return role;
                    }
                }
            }
        }

        return "";
    }

    private String findFirstKnownRole(Object rolesObject) {
        if (!(rolesObject instanceof Collection<?> roles)) {
            return "";
        }

        List<String> normalizedRoles = roles.stream()
                .filter(Objects::nonNull)
                .map(Object::toString)
                .map(this::normalizeRole)
                .toList();

        return ROLE_PRIORITY.stream()
                .filter(normalizedRoles::contains)
                .findFirst()
                .orElse("");
    }

    private String normalizeRole(String role) {
        return role == null ? "" : role.replaceFirst("^ROLE_", "").toUpperCase();
    }
}
