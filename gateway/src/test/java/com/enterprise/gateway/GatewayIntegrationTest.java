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
    private static final HttpServer BACKEND = startBackend();
    private static final HttpClient CLIENT = HttpClient.newHttpClient();

    @LocalServerPort
    private int port;

    @DynamicPropertySource
    static void routes(DynamicPropertyRegistry registry) {
        String url = "http://localhost:" + BACKEND.getAddress().getPort();
        for (String service : new String[] {"auth", "inventory", "order", "payment", "notification"}) {
            registry.add("services." + service + ".url", () -> url);
        }
    }

    @AfterAll
    static void stop() {
        BACKEND.stop(0);
    }

    @Test
    void publicCatalogueNeedsNoToken() throws Exception {
        HttpResponse<String> response = send(HttpRequest.newBuilder(uri("/api/products?page=0")).GET());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("/api/products");
        assertThat(LAST_HEADERS.get("x-correlation-id")).isNotBlank();
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

    private static HttpServer startBackend() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/", exchange -> {
                LAST_HEADERS.clear();
                exchange.getRequestHeaders().forEach((name, values) -> LAST_HEADERS.put(name.toLowerCase(), values.get(0)));
                byte[] body = ("{\"path\":\"" + exchange.getRequestURI().getPath() + "\"}").getBytes(StandardCharsets.UTF_8);
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
