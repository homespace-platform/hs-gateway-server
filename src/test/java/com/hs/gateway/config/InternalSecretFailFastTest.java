package com.hs.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.hs.gateway.config.security.GatewayRouteAuthorization;
import com.hs.gateway.config.security.UserAccessResolver;
import com.hs.gateway.filter.UserHeaderFilter;
import com.hs.gateway.dto.GatewayErrorResponseWriter;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class InternalSecretFailFastTest {
    @Test
    void feignConfigRejectsBlankSecretAndAddsHeaderOtherwise() {
        assertThatThrownBy(() -> new InternalFeignConfig().gatewayInternalSecretInterceptor(" "))
                .isInstanceOf(IllegalStateException.class);
        var template = new feign.RequestTemplate();
        new InternalFeignConfig().gatewayInternalSecretInterceptor("s3cret").apply(template);
        assertThat(template.headers().get("X-Internal-Secret")).containsExactly("s3cret");
    }

    @Test
    void headerFilterRejectsBlankSecretAtStartup() {
        var filter = new UserHeaderFilter(mock(UserAccessResolver.class), new GatewayRouteAuthorization(),
                mock(GatewayErrorResponseWriter.class));
        ReflectionTestUtils.setField(filter, "gatewayInternalSecret", "");
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(filter, "requireInternalSecret"))
                .isInstanceOf(IllegalStateException.class);
        ReflectionTestUtils.setField(filter, "gatewayInternalSecret", "s3cret");
        ReflectionTestUtils.invokeMethod(filter, "requireInternalSecret");
    }
}
