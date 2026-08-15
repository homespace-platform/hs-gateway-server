package com.hs.gateway.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import com.hs.gateway.dto.ApiResponse;
import com.hs.gateway.dto.UserPermissionsResponse;

@FeignClient(name = "hs-core-api", path = "/internal/users")
public interface UserInternalClient {

    @GetMapping("/{userId}/permissions")
    ApiResponse<UserPermissionsResponse> getUserPermissions(@PathVariable("userId") String userId);

}
