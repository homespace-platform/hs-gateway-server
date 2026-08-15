package com.hs.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.gateway.filter.factory.SpringCloudCircuitBreakerFilterFactory;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.cloud.gateway.route.builder.GatewayFilterSpec;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;

import com.hs.gateway.filter.InternalRateLimiterGatewayFilter;

@SpringBootApplication
@EnableWebFluxSecurity
@EnableFeignClients
public class HsGatewayServerApplication {

	private static final Logger log = LoggerFactory.getLogger(HsGatewayServerApplication.class);
	private static final String API_V1_PREFIX = "/api/v1";
	private static final String CORE_SERVICE_ROUTE_ID = "core-service-route";
	private static final String CORE_SERVICE_URI = "lb://hs-core-api";

	public static void main(String[] args) {
		SpringApplication.run(HsGatewayServerApplication.class, args);
	}

	@Bean
	public RouteLocator customRouteLocator(
			RouteLocatorBuilder builder,
			RedisRateLimiter defaultRateLimiter,
			KeyResolver ipKeyResolver,
			InternalRateLimiterGatewayFilter internalRateLimiterGatewayFilter,
			ObjectProvider<SpringCloudCircuitBreakerFilterFactory> circuitBreakerFilterFactory) {

		boolean circuitBreakerEnabled = circuitBreakerFilterFactory.getIfAvailable() != null;
		if (!circuitBreakerEnabled) {
			log.warn("Spring Cloud Gateway circuit breaker filter is not available. Gateway will start without per-route circuit breakers.");
		}

		return builder.routes()
				// Modular Monolith Core Service
				.route(CORE_SERVICE_ROUTE_ID, r -> r
						.path(API_V1_PREFIX + "/**")
						.filters(f -> commonFilters(
								f,
								defaultRateLimiter,
								ipKeyResolver,
								internalRateLimiterGatewayFilter,
								CORE_SERVICE_ROUTE_ID,
								API_V1_PREFIX,
								"userServiceCircuitBreaker",
								"forward:/fallback/user-service",
								circuitBreakerEnabled))
						.uri(CORE_SERVICE_URI))
				.build();
	}

	private GatewayFilterSpec commonFilters(
			GatewayFilterSpec filters,
			RedisRateLimiter defaultRateLimiter,
			KeyResolver ipKeyResolver,
			InternalRateLimiterGatewayFilter internalRateLimiterGatewayFilter,
			String routeId,
			String servicePath,
			String circuitBreakerName,
			String fallbackUri,
			boolean circuitBreakerEnabled) {

		GatewayFilterSpec spec = filters
				.rewritePath(servicePath + "/(?<segment>.*)", "/${segment}")
				.filter(internalRateLimiterGatewayFilter.apply(routeId, defaultRateLimiter, ipKeyResolver));

		if (!circuitBreakerEnabled)
			return spec;

		return spec.circuitBreaker(c -> c
				.setName(circuitBreakerName)
				.setFallbackUri(fallbackUri)
				.addStatusCode("500")
				.addStatusCode("502")
				.addStatusCode("503")
				.addStatusCode("504"));
	}
}
