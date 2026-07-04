package com.fit.iuh.gateway_server.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import com.fit.iuh.gateway_server.dto.ApiResponse;
import com.fit.iuh.gateway_server.dto.UserPermissionsResponse;

@FeignClient(name = "user-service", url = "${service.url.user-internal}", path = "/internal/users")
public interface UserInternalClient {

    @GetMapping("/{keycloakId}/permissions")
    ApiResponse<UserPermissionsResponse> getUserPermissions(@PathVariable("keycloakId") String keycloakId);

}
