package com.hs.gateway.dto;

import lombok.Builder;

import java.util.Set;

@Builder
public record UserPermissionsResponse(String email, String role, Set<String> permissions) {}
