package com.enterprise.gateway.config;

import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.setPath;
import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.uri;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;
import static org.springframework.cloud.gateway.server.mvc.predicate.GatewayRequestPredicates.path;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

/**
 * Public API routes. Only /api/** (plus the JWKS and API docs) is exposed; the services'
 * /internal/** endpoints are deliberately NOT routed, so they can't be reached through the gateway.
 * The merchant console (/api/merchant/**) is spread over three services: inventory, orders and finance.
 */
@Configuration
public class RoutesConfig {

    @Bean
    public RouterFunction<ServerResponse> apiRoutes(@Value("${services.auth.url}") String auth,
                                                    @Value("${services.inventory.url}") String inventory,
                                                    @Value("${services.order.url}") String order,
                                                    @Value("${services.payment.url}") String payment,
                                                    @Value("${services.notification.url}") String notification) {
        return route("auth-service")
                .route(path("/api/auth/**", "/api/users", "/api/users/**", "/api/merchants", "/api/merchants/**",
                        "/.well-known/jwks.json"), http())
                .before(uri(auth))
                .build()
                .and(route("product-inventory-service")
                        .route(path("/api/products", "/api/products/**", "/api/categories", "/api/categories/**",
                                "/api/merchant/products", "/api/merchant/products/**",
                                "/api/merchant/inventory", "/api/merchant/inventory/**",
                                "/api/merchant/purchases", "/api/merchant/purchases/**",
                                "/api/merchant/reports/inventory/**"), http())
                        .before(uri(inventory))
                        .build())
                .and(route("order-service")
                        .route(path("/api/orders", "/api/orders/**", "/api/reviews", "/api/reviews/**",
                                "/api/merchant/orders", "/api/merchant/orders/**", "/api/merchant/analytics",
                                "/api/merchant/reviews", "/api/merchant/reviews/**",
                                "/api/merchant/reports/sales/**"), http())
                        .before(uri(order))
                        .build())
                .and(route("payment-service")
                        .route(path("/api/payments", "/api/payments/**",
                                "/api/merchant/payments", "/api/merchant/payments/**",
                                "/api/merchant/expenses", "/api/merchant/expenses/**",
                                "/api/merchant/budgets", "/api/merchant/budgets/**",
                                "/api/merchant/finance/**", "/api/merchant/reports/profit-loss/**"), http())
                        .before(uri(payment))
                        .build())
                .and(route("notification-service")
                        .route(path("/api/notifications", "/api/notifications/**",
                                "/api/conversations", "/api/conversations/**"), http())
                        .before(uri(notification))
                        .build())
                .and(docs("auth", auth))
                .and(docs("inventory", inventory))
                .and(docs("orders", order))
                .and(docs("payments", payment))
                .and(docs("notifications", notification));
    }

    /** /api-docs/{service} -> {service}/v3/api-docs, used by the aggregated Swagger UI of the gateway. */
    private static RouterFunction<ServerResponse> docs(String name, String target) {
        return route(name + "-docs")
                .GET("/api-docs/" + name, http())
                .before(uri(target))
                .before(setPath("/v3/api-docs"))
                .build();
    }
}
