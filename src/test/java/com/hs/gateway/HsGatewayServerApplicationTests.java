package com.hs.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.config.import=optional:classpath:/gateway-test-only.properties",
        "SPRING_PROFILES_ACTIVE=test",
        "GATEWAY_INTERNAL_SECRET=gateway-context-test-secret-at-least-32-characters",
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "eureka.client.service-url.defaultZone=http://127.0.0.1:1/eureka/",
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://127.0.0.1:1/certs",
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://127.0.0.1:1/realms/test",
        "security.cors.allowed-origins=http://localhost:3000",
        "spring.data.redis.host=127.0.0.1",
        "spring.data.redis.port=1",
        "spring.data.redis.password=test-only",
        "logging.level.root=WARN",
        "logging.level.org.springframework.cloud.gateway.filter.ratelimit=WARN"
})
class HsGatewayServerApplicationTests {

	@Test
	void contextLoads() {
	}

}
