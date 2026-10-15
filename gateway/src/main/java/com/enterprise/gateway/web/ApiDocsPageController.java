package com.enterprise.gateway.web;

import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * GET /docs, the way into the API documentation.
 *
 * <p>On Render's free plan the services sleep when nobody visits, and only a request from outside Render wakes
 * one up: the gateway's own calls to a sleeping service get a 502 and leave it asleep. This page therefore wakes
 * every service from the visitor's browser ({@code app.wake-urls}) and opens Swagger UI as soon as all five
 * answer through the gateway. Where nothing sleeps (no {@code app.wake-urls}) it redirects to Swagger UI at once.
 */
@RestController
public class ApiDocsPageController {

    static final String SWAGGER_UI = "/swagger-ui.html";

    private static final String PAGE = """
            <!doctype html>
            <html lang="en">
            <head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <meta name="robots" content="noindex">
            <title>Nexora API</title>
            <style>
              :root { color-scheme: light dark; font-family: system-ui, -apple-system, "Segoe UI", sans-serif; }
              body { margin: 0; min-height: 100vh; display: grid; place-items: center; background: Canvas; color: CanvasText; }
              main { width: min(440px, calc(100vw - 32px)); }
              h1 { margin: 0 0 8px; font-size: 1.3rem; }
              p { margin: 0 0 20px; line-height: 1.55; opacity: 0.75; }
              ul { margin: 0; padding: 0; list-style: none; }
              li { display: flex; justify-content: space-between; gap: 16px; padding: 10px 0; border-top: 1px solid GrayText; }
              .ready { color: #1a7f37; font-weight: 600; }
            </style>
            </head>
            <body>
            <main>
              <h1>Starting the Nexora API</h1>
              <p>The live demo runs on free servers that sleep when nobody visits. Waking them takes up to two
                minutes; the API documentation opens by itself as soon as every service is ready.</p>
              <ul id="services"></ul>
            </main>
            <script>
              const WAKE_URLS = [__WAKE_URLS__];
              const SERVICES = [
                ["auth", "Accounts, stores and admin"],
                ["inventory", "Products and inventory"],
                ["orders", "Orders, analytics and reviews"],
                ["payments", "Payments and finance"],
                ["notifications", "Notifications and messages"],
              ];
              const status = {};
              for (const [id, name] of SERVICES) {
                const row = document.createElement("li");
                const label = document.createElement("span");
                label.textContent = name;
                status[id] = document.createElement("span");
                status[id].textContent = "starting\\u2026";
                row.append(label, status[id]);
                document.getElementById("services").append(row);
              }
              // the request alone wakes a service; its answer is not readable from this page and not needed
              const ping = () => WAKE_URLS.forEach((url) =>
                fetch(url + "/actuator/health", { mode: "no-cors", cache: "no-store" }).catch(() => {}));
              ping();
              setInterval(ping, 240000);
              const ready = new Set();
              async function check() {
                await Promise.all(SERVICES.map(async ([id]) => {
                  if (ready.has(id)) return;
                  try {
                    const response = await fetch("/api-docs/" + id, { cache: "no-store" });
                    if (response.ok) {
                      ready.add(id);
                      status[id].textContent = "ready";
                      status[id].className = "ready";
                    }
                  } catch {
                    // the gateway itself is still starting
                  }
                }));
                if (ready.size === SERVICES.length) location.replace("__SWAGGER_UI__");
                else setTimeout(check, 3000);
              }
              check();
            </script>
            </body>
            </html>
            """;

    private final List<String> wakeUrls;

    public ApiDocsPageController(@Value("${app.wake-urls:}") List<String> wakeUrls) {
        this.wakeUrls = wakeUrls.stream().map(String::trim).filter(url -> !url.isEmpty()).toList();
    }

    @GetMapping("/docs")
    public ResponseEntity<String> docs() {
        if (wakeUrls.isEmpty()) {
            return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(SWAGGER_UI)).build();
        }
        String urls = wakeUrls.stream().map(ApiDocsPageController::jsString).collect(Collectors.joining(", "));
        String page = PAGE.replace("__WAKE_URLS__", urls).replace("__SWAGGER_UI__", SWAGGER_UI);
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).cacheControl(CacheControl.noStore()).body(page);
    }

    /** A JavaScript string literal that is also safe inside an HTML script element. */
    static String jsString(String value) {
        StringBuilder literal = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            if (c < 0x20 || c == '"' || c == '\\' || c == '<' || c == '>' || c == '&' || c == '\'') {
                literal.append(String.format("\\u%04x", (int) c));
            } else {
                literal.append(c);
            }
        }
        return literal.append('"').toString();
    }
}
