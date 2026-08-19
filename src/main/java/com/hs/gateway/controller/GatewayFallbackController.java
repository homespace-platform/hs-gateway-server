package com.hs.gateway.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hs.gateway.advice.entity.enums.ErrorCode;
import com.hs.gateway.dto.ApiResponse;

import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/fallback")
public class GatewayFallbackController {

    @GetMapping("/{serviceName}")
    public Mono<ResponseEntity<ApiResponse<Void>>> getFallback(@PathVariable String serviceName) {
        return buildFallbackResponse(serviceName);
    }

    @PostMapping("/{serviceName}")
    public Mono<ResponseEntity<ApiResponse<Void>>> postFallback(@PathVariable String serviceName) {
        return buildFallbackResponse(serviceName);
    }

    @PutMapping("/{serviceName}")
    public Mono<ResponseEntity<ApiResponse<Void>>> putFallback(@PathVariable String serviceName) {
        return buildFallbackResponse(serviceName);
    }

    @PatchMapping("/{serviceName}")
    public Mono<ResponseEntity<ApiResponse<Void>>> patchFallback(@PathVariable String serviceName) {
        return buildFallbackResponse(serviceName);
    }

    @DeleteMapping("/{serviceName}")
    public Mono<ResponseEntity<ApiResponse<Void>>> deleteFallback(@PathVariable String serviceName) {
        return buildFallbackResponse(serviceName);
    }

    private Mono<ResponseEntity<ApiResponse<Void>>> buildFallbackResponse(String serviceName) {
        ErrorCode errorCode = ErrorCode.SERVICE_UNAVAILABLE;
        ApiResponse<Void> body = ApiResponse.<Void>builder()
                .code(errorCode.getCode())
                .message("Service " + serviceName + " is temporarily unavailable. Please try again later.")
                .build();
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body));
    }
}

