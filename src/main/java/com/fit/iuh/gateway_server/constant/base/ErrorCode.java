package com.fit.iuh.gateway_server.constant.base;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Getter
public enum ErrorCode {
    BAD_REQUEST(1001, "Bad request", HttpStatus.BAD_REQUEST),
    ROUTE_NOT_FOUND(1005, "Route not found", HttpStatus.NOT_FOUND),
    METHOD_NOT_ALLOWED(1006, "Method not allowed", HttpStatus.METHOD_NOT_ALLOWED),
    UNCATEGORIZED_EXCEPTION(9999, "Uncategorized error", HttpStatus.INTERNAL_SERVER_ERROR),
    UNAUTHENTICATED(1002, "Unauthenticated", HttpStatus.UNAUTHORIZED),
    UNAUTHORIZED(1003, "You do not have permission", HttpStatus.FORBIDDEN),
    TOO_MANY_REQUESTS(1004, "Too many requests", HttpStatus.TOO_MANY_REQUESTS),
    SERVICE_UNAVAILABLE(9001, "Service is temporarily unavailable. Please try again later.", HttpStatus.SERVICE_UNAVAILABLE),
    GATEWAY_TIMEOUT(9002, "Gateway timeout", HttpStatus.GATEWAY_TIMEOUT),
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
