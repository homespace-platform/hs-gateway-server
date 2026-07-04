package com.fit.iuh.gateway_server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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

@SpringBootApplication
@EnableWebFluxSecurity
@EnableFeignClients
public class GatewayServerApplication {

	private static final Logger log = LoggerFactory.getLogger(GatewayServerApplication.class);

	@Value("${service.url.user}")
	private String userServiceUrl;

	public static void main(String[] args) {
		SpringApplication.run(GatewayServerApplication.class, args);
	}

	@Bean
	public RouteLocator customRouteLocator(
			RouteLocatorBuilder builder,
			RedisRateLimiter defaultRateLimiter,
			KeyResolver ipKeyResolver,
			ObjectProvider<SpringCloudCircuitBreakerFilterFactory> circuitBreakerFilterFactory) {

		boolean circuitBreakerEnabled = circuitBreakerFilterFactory.getIfAvailable() != null;
		if (!circuitBreakerEnabled) {
			log.warn("Spring Cloud Gateway circuit breaker filter is not available. Gateway will start without per-route circuit breakers.");
		}

		return builder.routes()
				// User Service
				.route("user-service-route", r -> r
						.path("/user-service/**")
						.filters(f -> commonFilters(
								f,
								defaultRateLimiter,
								ipKeyResolver,
								"/user-service",
								"userServiceCircuitBreaker",
								"forward:/fallback/user-service",
								circuitBreakerEnabled))
						.uri(userServiceUrl))
				.build();
	}

	private GatewayFilterSpec commonFilters(
			GatewayFilterSpec filters,
			RedisRateLimiter defaultRateLimiter,
			KeyResolver ipKeyResolver,
			String servicePath,
			String circuitBreakerName,
			String fallbackUri,
			boolean circuitBreakerEnabled) {

		GatewayFilterSpec spec = filters
				.rewritePath(servicePath + "/(?<segment>.*)", "/${segment}")
				.requestRateLimiter(c -> c.setRateLimiter(defaultRateLimiter).setKeyResolver(ipKeyResolver));

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
