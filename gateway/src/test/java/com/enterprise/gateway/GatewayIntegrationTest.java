package com.enterprise.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.gateway.security.KeyResolver;
import com.sun.net.httpserver.HttpServer;
import io.jsonwebtoken.Jwts;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Starts the real gateway in front of a fake backend and checks routing, JWT checks, CORS and headers. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(GatewayIntegrationTest.TestKeys.class)
class GatewayIntegrationTest {

    private static final KeyPair KEYS = generate();
    private static final Map<String, String> LAST_HEADERS = new ConcurrentHashMap<>();
    private static final String[] SERVICES = {"auth", "inventory", "order", "payment", "notification"};
    private static final Map<String, HttpServer> BACKENDS = startBackends();
    private static final HttpClient CLIENT = HttpClient.newHttpClient();

    @LocalServerPort
    private int port;

    @DynamicPropertySource
    static void routes(DynamicPropertyRegistry registry) {
        BACKENDS.forEach((service, server) -> registry.add("services." + service + ".url",
                () -> "http://localhost:" + server.getAddress().getPort()));
    }

    @AfterAll
    static void stop() {
        BACKENDS.values().forEach(server -> server.stop(0));
    }

    @Test
    void publicCatalogueNeedsNoToken() throws Exception {
        HttpResponse<String> response = send(HttpRequest.newBuilder(uri("/api/products?page=0")).GET());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("/api/products");
        assertThat(LAST_HEADERS.get("x-correlation-id")).isNotBlank();
        for (String path : new String[] {"/api/sellers", "/api/sellers/12", "/api/reviews?productId=3",
                "/api/categories", "/api/announcements"}) {
            assertThat(send(HttpRequest.newBuilder(uri(path)).GET()).statusCode()).as(path).isEqualTo(200);
        }
        assertThat(send(HttpRequest.newBuilder(uri("/api/reviews/mine")).GET()).statusCode()).isEqualTo(401);
        assertThat(send(HttpRequest.newBuilder(uri("/api/sellers/12")).POST(HttpRequest.BodyPublishers.noBody()))
                .statusCode()).isEqualTo(401);
    }

    @Test
    void everyApiPathIsRoutedToTheRightService() throws Exception {
        Map<String, String> expected = Map.ofEntries(
                Map.entry("/api/users/me", "auth"),
                Map.entry("/api/sellers/5", "auth"),
                Map.entry("/api/admin/users", "auth"),
                Map.entry("/api/admin/users/9", "auth"),
                Map.entry("/api/admin/security/logins", "auth"),
                Map.entry("/api/products/9", "inventory"),
                Map.entry("/api/inventory/9", "inventory"),
                Map.entry("/api/seller/products", "inventory"),
                Map.entry("/api/seller/inventory/summary", "inventory"),
                Map.entry("/api/seller/warehouses", "inventory"),
                Map.entry("/api/seller/transfers/3", "inventory"),
                Map.entry("/api/seller/suppliers", "inventory"),
                Map.entry("/api/seller/purchase-orders/2/pdf", "inventory"),
                Map.entry("/api/seller/goods-receipts", "inventory"),
                Map.entry("/api/seller/stock-counts/1/sheet", "inventory"),
                Map.entry("/api/seller/replenishment", "inventory"),
                Map.entry("/api/seller/store/settings", "inventory"),
                Map.entry("/api/seller/reports/inventory/valuation/pdf", "inventory"),
                Map.entry("/api/admin/catalog/products", "inventory"),
                Map.entry("/api/admin/inventory/summary", "inventory"),
                Map.entry("/api/orders/customer/7", "order"),
                Map.entry("/api/orders/4/invoice", "order"),
                Map.entry("/api/reviews/mine", "order"),
                Map.entry("/api/seller/orders/4", "order"),
                Map.entry("/api/seller/analytics", "order"),
                Map.entry("/api/seller/customers", "order"),
                Map.entry("/api/seller/reviews", "order"),
                Map.entry("/api/seller/reports/sales/xlsx", "order"),
                Map.entry("/api/admin/orders/4", "order"),
                Map.entry("/api/admin/sales/overview", "order"),
                Map.entry("/api/admin/customers/7/purchases", "order"),
                Map.entry("/api/admin/reviews", "order"),
                Map.entry("/api/payments", "payment"),
                Map.entry("/api/payments/12", "payment"),
                Map.entry("/api/seller/payments/summary", "payment"),
                Map.entry("/api/seller/expenses", "payment"),
                Map.entry("/api/seller/budgets", "payment"),
                Map.entry("/api/seller/finance/profit-loss", "payment"),
                Map.entry("/api/seller/reports/profit-loss/pdf", "payment"),
                Map.entry("/api/admin/payments/summary", "payment"),
                Map.entry("/api/admin/sellers/5/earnings", "payment"),
                Map.entry("/api/admin/sellers/5/profit-loss", "payment"),
                Map.entry("/api/notifications/unread-count", "notification"),
                Map.entry("/api/conversations/3/messages", "notification"),
                Map.entry("/api/admin/conversations/3/messages", "notification"),
                Map.entry("/api/admin/messages", "notification"),
                Map.entry("/api/admin/notifications", "notification"),
                Map.entry("/api/admin/emails", "notification"),
                Map.entry("/api/admin/announcements", "notification"));
        String token = "Bearer " + token();
        for (Map.Entry<String, String> route : expected.entrySet()) {
            HttpResponse<String> response = send(HttpRequest.newBuilder(uri(route.getKey()))
                    .header("Authorization", token).GET());
            assertThat(response.statusCode()).as(route.getKey()).isEqualTo(200);
            assertThat(response.body()).as(route.getKey())
                    .contains("\"service\":\"" + route.getValue() + "\"")
                    .contains("\"path\":\"" + route.getKey() + "\"");
        }
        assertThat(send(HttpRequest.newBuilder(uri("/api/seller/unknown")).header("Authorization", token).GET())
                .statusCode()).isEqualTo(404);
    }

    @Test
    void protectedRoutesRequireAValidToken() throws Exception {
        assertThat(send(HttpRequest.newBuilder(uri("/api/orders")).GET()).statusCode()).isEqualTo(401);
        assertThat(send(HttpRequest.newBuilder(uri("/api/orders")).header("Authorization", "Bearer garbage").GET())
                .statusCode()).isEqualTo(401);

        HttpResponse<String> ok = send(HttpRequest.newBuilder(uri("/api/orders"))
                .header("Authorization", "Bearer " + token()).GET());
        assertThat(ok.statusCode()).isEqualTo(200);
        assertThat(LAST_HEADERS.get("authorization")).startsWith("Bearer ");
    }

    @Test
    void theAdministratorConsoleSignsInWithoutATokenButIsRateLimited() throws Exception {
        int status = 0;
        for (int attempt = 1; attempt <= 6; attempt++) {
            status = send(HttpRequest.newBuilder(uri("/api/admin/auth/login"))
                    .header("Content-Type", "application/json")
                    .header("X-Forwarded-For", "203.0.113.77")
                    .POST(HttpRequest.BodyPublishers.ofString("{}"))).statusCode();
            if (attempt <= 5) {
                assertThat(status).as("attempt " + attempt).isEqualTo(200);
            }
        }
        assertThat(status).isEqualTo(429);
        assertThat(send(HttpRequest.newBuilder(uri("/api/admin/orders")).GET()).statusCode()).isEqualTo(401);
    }

    @Test
    void theApiDocumentationPageOpensSwaggerAtOnceWhereNothingSleeps() throws Exception {
        HttpResponse<String> response = send(HttpRequest.newBuilder(uri("/docs")).GET());
        assertThat(response.statusCode()).isEqualTo(302);
        assertThat(response.headers().firstValue("Location")).hasValue("/swagger-ui.html");
    }

    @Test
    void internalEndpointsAreNotExposed() throws Exception {
        HttpResponse<String> response = send(HttpRequest.newBuilder(uri("/internal/orders/1/payment-outcome"))
                .header("Authorization", "Bearer " + token()).POST(HttpRequest.BodyPublishers.ofString("{}")));
        assertThat(response.statusCode()).isEqualTo(404);
    }

    @Test
    void corsPreflightIsAnsweredForTheFrontend() throws Exception {
        HttpResponse<String> response = send(HttpRequest.newBuilder(uri("/api/orders"))
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .header("Origin", "https://enterprise-frontend-wvds.onrender.com")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "authorization,content-type"));
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Access-Control-Allow-Origin"))
                .hasValue("https://enterprise-frontend-wvds.onrender.com");

        HttpResponse<String> admin = send(HttpRequest.newBuilder(uri("/api/admin/orders"))
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .header("Origin", "https://enterprise-admin.onrender.com")
                .header("Access-Control-Request-Method", "GET")
                .header("Access-Control-Request-Headers", "authorization"));
        assertThat(admin.headers().firstValue("Access-Control-Allow-Origin"))
                .hasValue("https://enterprise-admin.onrender.com");
        HttpResponse<String> stranger = send(HttpRequest.newBuilder(uri("/api/orders"))
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .header("Origin", "https://evil.example.com")
                .header("Access-Control-Request-Method", "GET"));
        assertThat(stranger.headers().firstValue("Access-Control-Allow-Origin")).isEmpty();

        HttpResponse<String> download = send(HttpRequest.newBuilder(uri("/api/seller/reports/sales/pdf"))
                .header("Origin", "https://enterprise-frontend-wvds.onrender.com")
                .header("Authorization", "Bearer " + token()).GET());
        assertThat(download.headers().firstValue("Access-Control-Expose-Headers").orElse(""))
                .contains("Content-Disposition");
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private static HttpResponse<String> send(HttpRequest.Builder builder) throws Exception {
        return CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String token() {
        return Jwts.builder().header().keyId("k1").and()
                .issuer("enterprise-auth").subject("7").claim("username", "kush").claim("role", "CUSTOMER")
                .issuedAt(new Date()).expiration(Date.from(Instant.now().plusSeconds(600)))
                .signWith(KEYS.getPrivate(), Jwts.SIG.RS256).compact();
    }

    private static Map<String, HttpServer> startBackends() {
        Map<String, HttpServer> servers = new ConcurrentHashMap<>();
        for (String service : SERVICES) {
            servers.put(service, startBackend(service));
        }
        return servers;
    }

    private static HttpServer startBackend(String service) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/", exchange -> {
                LAST_HEADERS.clear();
                exchange.getRequestHeaders().forEach((name, values) -> LAST_HEADERS.put(name.toLowerCase(), values.get(0)));
                byte[] body = ("{\"service\":\"" + service + "\",\"path\":\"" + exchange.getRequestURI().getPath()
                        + "\"}").getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(body);
                }
            });
            server.start();
            return server;
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static KeyPair generate() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    @TestConfiguration
    static class TestKeys {
        @Bean
        @Primary
        KeyResolver testKeyResolver() {
            return keyId -> KEYS.getPublic();
        }
    }
}
