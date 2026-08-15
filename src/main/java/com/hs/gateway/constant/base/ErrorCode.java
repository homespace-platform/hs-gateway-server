package com.hs.gateway.constant.base;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Getter
public enum ErrorCode {
    // 1xxx — platform / gateway
    BAD_REQUEST(1001, "Bad request", HttpStatus.BAD_REQUEST),
    UNAUTHENTICATED(1002, "Unauthenticated", HttpStatus.UNAUTHORIZED),
    UNAUTHORIZED(1003, "You do not have permission", HttpStatus.FORBIDDEN),
    ROUTE_NOT_FOUND(1004, "Route not found", HttpStatus.NOT_FOUND),
    METHOD_NOT_ALLOWED(1005, "Method not allowed", HttpStatus.METHOD_NOT_ALLOWED),
    TOO_MANY_REQUESTS(1006, "Too many requests", HttpStatus.TOO_MANY_REQUESTS),
    SERVICE_UNAVAILABLE(1007, "Service is temporarily unavailable. Please try again later.", HttpStatus.SERVICE_UNAVAILABLE),
    GATEWAY_TIMEOUT(1008, "Gateway timeout", HttpStatus.GATEWAY_TIMEOUT),
    UNCATEGORIZED_EXCEPTION(9999, "Uncategorized error", HttpStatus.INTERNAL_SERVER_ERROR),
    ;

    int code;
    String message;
    HttpStatusCode statusCode;

    ErrorCode(int code, String message, HttpStatusCode statusCode) {
        this.code = code;
        this.message = message;
        this.statusCode = statusCode;
    }
}
