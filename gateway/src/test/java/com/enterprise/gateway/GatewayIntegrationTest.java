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
        for (String path : new String[] {"/api/merchants", "/api/merchants/12", "/api/reviews?productId=3",
                "/api/categories"}) {
            assertThat(send(HttpRequest.newBuilder(uri(path)).GET()).statusCode()).as(path).isEqualTo(200);
        }
        assertThat(send(HttpRequest.newBuilder(uri("/api/reviews/mine")).GET()).statusCode()).isEqualTo(401);
        assertThat(send(HttpRequest.newBuilder(uri("/api/merchants/12")).POST(HttpRequest.BodyPublishers.noBody()))
                .statusCode()).isEqualTo(401);
    }

    @Test
    void everyApiPathIsRoutedToTheRightService() throws Exception {
        Map<String, String> expected = Map.ofEntries(
                Map.entry("/api/users/me", "auth"),
                Map.entry("/api/merchants/5", "auth"),
                Map.entry("/api/products/9", "inventory"),
                Map.entry("/api/merchant/products", "inventory"),
                Map.entry("/api/merchant/inventory/summary", "inventory"),
                Map.entry("/api/merchant/purchases", "inventory"),
                Map.entry("/api/merchant/reports/inventory/pdf", "inventory"),
                Map.entry("/api/orders/stats", "order"),
                Map.entry("/api/reviews/mine", "order"),
                Map.entry("/api/merchant/orders/4", "order"),
                Map.entry("/api/merchant/analytics", "order"),
                Map.entry("/api/merchant/reviews", "order"),
                Map.entry("/api/merchant/reports/sales/xlsx", "order"),
                Map.entry("/api/payments", "payment"),
                Map.entry("/api/merchant/payments/summary", "payment"),
                Map.entry("/api/merchant/expenses", "payment"),
                Map.entry("/api/merchant/budgets", "payment"),
                Map.entry("/api/merchant/finance/profit-loss", "payment"),
                Map.entry("/api/merchant/reports/profit-loss/pdf", "payment"),
                Map.entry("/api/notifications/unread-count", "notification"),
                Map.entry("/api/conversations/3/messages", "notification"));
        String token = "Bearer " + token();
        for (Map.Entry<String, String> route : expected.entrySet()) {
            HttpResponse<String> response = send(HttpRequest.newBuilder(uri(route.getKey()))
                    .header("Authorization", token).GET());
            assertThat(response.statusCode()).as(route.getKey()).isEqualTo(200);
            assertThat(response.body()).as(route.getKey())
                    .contains("\"service\":\"" + route.getValue() + "\"")
                    .contains("\"path\":\"" + route.getKey() + "\"");
        }
        assertThat(send(HttpRequest.newBuilder(uri("/api/merchant/unknown")).header("Authorization", token).GET())
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
    void internalEndpointsAreNotExposed() throws Exception {
        HttpResponse<String> response = send(HttpRequest.newBuilder(uri("/internal/payments"))
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

        HttpResponse<String> download = send(HttpRequest.newBuilder(uri("/api/merchant/reports/sales/pdf"))
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
