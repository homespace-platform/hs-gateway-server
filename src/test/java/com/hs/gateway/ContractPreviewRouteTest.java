package com.hs.gateway;

import com.hs.gateway.filter.InternalRateLimiterGatewayFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.webflux.autoconfigure.WebFluxProperties;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreakerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.RewritePathGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.SpringCloudCircuitBreakerFilterFactory;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.handler.predicate.*;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.reactive.DispatcherHandler;
import reactor.core.publisher.Mono;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ContractPreviewRouteTest {
    @Test void postPreviewUsesDedicatedCircuitBeforeCoreAndPreservesServerErrors() throws Exception {
        try (var context = new StaticApplicationContext()) {
            var configs = new HashMap<String, SpringCloudCircuitBreakerFilterFactory.Config>();
            GatewayFilter passthrough = (exchange, chain) -> chain.filter(exchange);
            @SuppressWarnings("unchecked")
            ObjectProvider<DispatcherHandler> dispatcher = mock(ObjectProvider.class);
            var circuits = new SpringCloudCircuitBreakerFilterFactory(mock(ReactiveCircuitBreakerFactory.class), dispatcher) {
                @Override public GatewayFilter apply(Config config) {
                    configs.put(config.getName(), config); return passthrough;
                }
                @Override protected Mono<Void> handleErrorWithoutFallback(Throwable error, boolean resume) {
                    return Mono.error(error);
                }
            };
            context.getBeanFactory().registerSingleton("path", new PathRoutePredicateFactory(new WebFluxProperties()));
            context.getBeanFactory().registerSingleton("method", new MethodRoutePredicateFactory());
            context.getBeanFactory().registerSingleton("header", new HeaderRoutePredicateFactory());
            context.getBeanFactory().registerSingleton("rewrite", new RewritePathGatewayFilterFactory());
            context.getBeanFactory().registerSingleton("circuits", circuits);
            context.refresh();
            var limiter = mock(InternalRateLimiterGatewayFilter.class);
            when(limiter.apply(anyString(), any(), any())).thenReturn(passthrough);
            var routes = new HsGatewayServerApplication().customRouteLocator(new RouteLocatorBuilder(context),
                    mock(RedisRateLimiter.class), mock(KeyResolver.class), limiter,
                    context.getBeanProvider(SpringCloudCircuitBreakerFilterFactory.class)).getRoutes().collectList().block();
            assertEquals("contract-preview-route", firstMatch(routes, HttpMethod.POST, "/api/v1/contracts/c1/previews").getId());
            assertEquals("core-service-route", firstMatch(routes, HttpMethod.GET, "/api/v1/contracts/c1/preview-status").getId());
            assertEquals("core-service-route", firstMatch(routes, HttpMethod.GET, "/api/v1/contracts/c1/previews").getId());
            assertTrue(configs.get("contractPreviewCircuitBreaker").getStatusCodes().isEmpty());
            assertFalse(configs.get("coreServiceCircuitBreaker").getStatusCodes().isEmpty());
            var properties = new Properties();
            try (var stream = getClass().getResourceAsStream("/application.properties")) { properties.load(stream); }
            assertEquals("90s", properties.getProperty("resilience4j.timelimiter.instances.contractPreviewCircuitBreaker.timeout-duration"));
            assertEquals("6s", properties.getProperty("resilience4j.timelimiter.configs.default.timeout-duration"));
        }
    }
    private Route firstMatch(List<Route> routes, HttpMethod method, String path) {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.method(method, path).build());
        return routes.stream().filter(route -> Boolean.TRUE.equals(Mono.from(route.getPredicate().apply(exchange)).block()))
                .findFirst().orElseThrow();
    }
}
