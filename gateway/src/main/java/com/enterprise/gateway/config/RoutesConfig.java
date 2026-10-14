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
 * Public API routes. Only /api/** (plus the JWKS and API docs) is exposed; the services' /internal/** endpoints
 * are deliberately NOT routed, so they can't be reached through the gateway.
 * <ul>
 *   <li>Seller Central (/api/seller/**) is spread over three services: inventory, orders and finance.</li>
 *   <li>The Nexora Control Center (/api/admin/**) reads from all five services.</li>
 * </ul>
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
                .route(path("/api/auth/**", "/api/users", "/api/users/**", "/api/sellers", "/api/sellers/**",
                        "/api/admin/auth/**", "/api/admin/users", "/api/admin/users/**", "/api/admin/security/**",
                        "/.well-known/jwks.json"), http())
                .before(uri(auth))
                .build()
                .and(route("product-service")
                        .route(path("/api/products", "/api/products/**", "/api/categories", "/api/categories/**",
                                "/api/inventory/**",
                                "/api/seller/products", "/api/seller/products/**",
                                "/api/seller/inventory", "/api/seller/inventory/**",
                                "/api/seller/warehouses", "/api/seller/warehouses/**",
                                "/api/seller/transfers", "/api/seller/transfers/**",
                                "/api/seller/suppliers", "/api/seller/suppliers/**",
                                "/api/seller/purchase-orders", "/api/seller/purchase-orders/**",
                                "/api/seller/goods-receipts", "/api/seller/goods-receipts/**",
                                "/api/seller/stock-counts", "/api/seller/stock-counts/**",
                                "/api/seller/replenishment", "/api/seller/replenishment/**",
                                "/api/seller/store/**", "/api/seller/reports/inventory/**",
                                "/api/admin/catalog/**", "/api/admin/inventory/**"), http())
                        .before(uri(inventory))
                        .build())
                .and(route("order-service")
                        .route(path("/api/orders", "/api/orders/**", "/api/reviews", "/api/reviews/**",
                                "/api/seller/orders", "/api/seller/orders/**", "/api/seller/analytics",
                                "/api/seller/customers", "/api/seller/customers/**",
                                "/api/seller/reviews", "/api/seller/reviews/**", "/api/seller/reports/sales/**",
                                "/api/admin/orders", "/api/admin/orders/**", "/api/admin/sales/**",
                                "/api/admin/customers/*/purchases",
                                "/api/admin/reviews", "/api/admin/reviews/**"), http())
                        .before(uri(order))
                        .build())
                .and(route("payment-service")
                        .route(path("/api/payments", "/api/payments/**",
                                "/api/seller/payments", "/api/seller/payments/**",
                                "/api/seller/expenses", "/api/seller/expenses/**",
                                "/api/seller/budgets", "/api/seller/budgets/**",
                                "/api/seller/finance/**", "/api/seller/reports/profit-loss/**",
                                "/api/admin/payments", "/api/admin/payments/**",
                                "/api/admin/sellers/*/earnings", "/api/admin/sellers/*/profit-loss"), http())
                        .before(uri(payment))
                        .build())
                .and(route("notification-service")
                        .route(path("/api/notifications", "/api/notifications/**",
                                "/api/conversations", "/api/conversations/**", "/api/announcements",
                                "/api/admin/conversations", "/api/admin/conversations/**",
                                "/api/admin/messages", "/api/admin/notifications", "/api/admin/emails",
                                "/api/admin/announcements", "/api/admin/announcements/**"), http())
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
