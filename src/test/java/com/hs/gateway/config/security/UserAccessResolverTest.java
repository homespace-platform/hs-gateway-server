package com.hs.gateway.config.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import com.hs.gateway.client.UserInternalClient;
import com.hs.gateway.dto.ApiResponse;
import com.hs.gateway.dto.UserPermissionsResponse;

class UserAccessResolverTest {

    private final UserInternalClient client = mock(UserInternalClient.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<UserInternalClient> provider = mock(ObjectProvider.class);
    private final UserAccessResolver resolver = new UserAccessResolver(provider);
    // The JWT claims ADMIN: the resolver must never trust it.
    private final JwtAuthenticationToken adminJwt = new JwtAuthenticationToken(
            Jwt.withTokenValue("t").header("alg", "none").subject("u-1")
                    .claim("realm_access", Map.of("roles", List.of("ADMIN"))).build());

    private ApiResponse<UserPermissionsResponse> response(String role, Set<String> permissions) {
        ApiResponse<UserPermissionsResponse> r = new ApiResponse<>();
        r.setResult(new UserPermissionsResponse("a@b.c", role, permissions));
        return r;
    }

    @Test
    void lookupErrorFailsClosedToUserWithoutAuthorities() {
        when(provider.getIfAvailable()).thenReturn(client);
        when(client.getUserPermissions("u-1")).thenThrow(new IllegalStateException("down"));
        var access = resolver.resolve(adminJwt).block();
        assertThat(access.role()).isEqualTo("USER");
        assertThat(access.authorities()).isEmpty();
    }

    @Test
    void missingClientOrNullRoleFailsClosed() {
        when(provider.getIfAvailable()).thenReturn(null);
        assertThat(resolver.resolve(adminJwt).block().role()).isEqualTo("USER");
        when(provider.getIfAvailable()).thenReturn(client);
        when(client.getUserPermissions("u-1")).thenReturn(response(null, Set.of("USER_VIEW")));
        var access = resolver.resolve(adminJwt).block();
        assertThat(access.role()).isEqualTo("USER");
        assertThat(access.authorities()).isEmpty();
    }

    @Test
    void databaseRoleIsUsedWhenKnown() {
        when(provider.getIfAvailable()).thenReturn(client);
        when(client.getUserPermissions("u-1")).thenReturn(response("ROLE_admin", Set.of("USER_VIEW")));
        var access = resolver.resolve(adminJwt).block();
        assertThat(access.role()).isEqualTo("ADMIN");
        assertThat(access.authorities()).isEqualTo("USER_VIEW");
    }
}
