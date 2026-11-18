package com.hs.gateway.config.security;

import java.util.Set;

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

    private static final Set<String> KNOWN_ROLES = Set.of("ADMIN", "USER");
    /** Fail-closed result: never derived from the JWT, so a lookup failure can never grant ADMIN. */
    private static final UserAccess DENIED = new UserAccess("USER", "");

    private final ObjectProvider<@NonNull UserInternalClient> userInternalClientProvider;

    public Mono<@NonNull UserAccess> resolve(JwtAuthenticationToken jwtAuth) {
        String userId = jwtAuth.getToken().getSubject();

        return Mono.fromCallable(() -> {
                    UserInternalClient client = userInternalClientProvider.getIfAvailable();
                    return (client != null) ? client.getUserPermissions(userId) : null;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .map(response -> {
                    if (response == null || response.getResult() == null) return DENIED;
                    String role = normalizeRole(response.getResult().role());
                    if (!KNOWN_ROLES.contains(role)) return DENIED;
                    String authorities = response.getResult().permissions() != null
                            ? String.join(",", response.getResult().permissions())
                            : "";
                    return new UserAccess(role, authorities);
                })
                .defaultIfEmpty(DENIED)
                .onErrorResume(exception -> {
                    log.warn("Could not fetch user access for userId {}. Treating as USER without authorities. Reason: {}",
                            userId, exception.getMessage());
                    return Mono.just(DENIED);
                });
    }

    private String normalizeRole(String role) {
        return role == null ? "" : role.replaceFirst("^ROLE_", "").toUpperCase();
    }
}
