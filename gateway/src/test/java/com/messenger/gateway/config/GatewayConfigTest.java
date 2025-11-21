package com.messenger.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class GatewayConfigTest {

    @Autowired
    private GatewayConfig config;

    @Autowired
    private RouteLocatorBuilder builder;

    @Test
    void testCustomRouteLocator() {
        RouteLocator locator = config.customRouteLocator(builder);
        assertNotNull(locator);
    }
}
