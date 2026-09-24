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
	private static final String CHAT_SERVICE_ROUTE_ID = "chat-service-route";
	private static final String CHAT_SOCKET_ROUTE_ID = "chat-socket-route";
	private static final String CHAT_WEBSOCKET_ROUTE_ID = "chat-websocket-route";
	private static final String CHAT_SERVICE_PATH = API_V1_PREFIX + "/chat";
	private static final String CHAT_SERVICE_URI = "lb://hs-chat-service";
	private static final String CHAT_WEBSOCKET_URI = "lb:ws://hs-chat-service";
	private static final String NEWS_SERVICE_ROUTE_ID = "news-service-route";
	private static final String NEWS_SERVICE_DIAGNOSTICS_ROUTE_ID = "news-service-diagnostics-route";
	private static final String NEWS_SERVICE_PATH = API_V1_PREFIX + "/news";
	private static final String NEWS_SERVICE_URI = "lb://hs-news-service";
	private static final String AI_SERVICE_ROUTE_ID = "ai-service-route";
	private static final String AI_SERVICE_PATH = API_V1_PREFIX + "/ai";
	private static final String AI_SERVICE_URI = "lb://hs-ai-service";

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
				// Standalone News Service
				.route(NEWS_SERVICE_ROUTE_ID, r -> r
						.path(API_V1_PREFIX + "/admin/news/**", API_V1_PREFIX + "/public/news/**")
						.filters(f -> commonFilters(
								f,
								defaultRateLimiter,
								ipKeyResolver,
								internalRateLimiterGatewayFilter,
								NEWS_SERVICE_ROUTE_ID,
								API_V1_PREFIX,
								"newsServiceCircuitBreaker",
								"forward:/fallback/news-service",
								circuitBreakerEnabled))
						.uri(NEWS_SERVICE_URI))
				.route(NEWS_SERVICE_DIAGNOSTICS_ROUTE_ID, r -> r
						.path(NEWS_SERVICE_PATH + "/**")
						.filters(f -> commonFilters(
								f,
								defaultRateLimiter,
								ipKeyResolver,
								internalRateLimiterGatewayFilter,
								NEWS_SERVICE_DIAGNOSTICS_ROUTE_ID,
								NEWS_SERVICE_PATH,
								"newsServiceCircuitBreaker",
								"forward:/fallback/news-service",
								circuitBreakerEnabled))
						.uri(NEWS_SERVICE_URI))
				.route(CHAT_WEBSOCKET_ROUTE_ID, r -> r
						.path(CHAT_SERVICE_PATH + "/socket.io/**")
						.and()
						.header("Upgrade", "(?i)websocket")
						.filters(f -> f.rewritePath(
								CHAT_SERVICE_PATH + "/(?<segment>.*)",
								"/${segment}"))
						.uri(CHAT_WEBSOCKET_URI))
				.route(CHAT_SOCKET_ROUTE_ID, r -> r
						.path(CHAT_SERVICE_PATH + "/socket.io/**")
						.filters(f -> f.rewritePath(
								CHAT_SERVICE_PATH + "/(?<segment>.*)",
								"/${segment}"))
						.uri(CHAT_SERVICE_URI))
				// Standalone Chat Service (must be declared before the core catch-all route)
				.route(CHAT_SERVICE_ROUTE_ID, r -> r
						.path(CHAT_SERVICE_PATH + "/**")
						.filters(f -> commonFilters(
								f,
								defaultRateLimiter,
								ipKeyResolver,
								internalRateLimiterGatewayFilter,
								CHAT_SERVICE_ROUTE_ID,
								CHAT_SERVICE_PATH,
								"chatServiceCircuitBreaker",
								"forward:/fallback/chat-service",
									circuitBreakerEnabled))
						.uri(CHAT_SERVICE_URI))
				// HomeSpace AI Service (keep before the core catch-all route)
				.route(AI_SERVICE_ROUTE_ID, r -> r
						.path(AI_SERVICE_PATH + "/**")
						.filters(f -> commonFilters(
								f,
								defaultRateLimiter,
								ipKeyResolver,
								internalRateLimiterGatewayFilter,
								AI_SERVICE_ROUTE_ID,
								AI_SERVICE_PATH,
								"aiServiceCircuitBreaker",
								"forward:/fallback/ai-service",
								circuitBreakerEnabled))
						.uri(AI_SERVICE_URI))
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
								"coreServiceCircuitBreaker",
								"forward:/fallback/core-service",
								circuitBreakerEnabled))
						.uri(CORE_SERVICE_URI))
				// Mobile Proof Upload Route (Cross-device handoff without prefix stripping)
				.route("mobile-proof-upload-route", r -> r
						.path("/u/**")
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
