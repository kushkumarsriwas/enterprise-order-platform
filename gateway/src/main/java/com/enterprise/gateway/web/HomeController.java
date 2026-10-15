package com.enterprise.gateway.web;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HomeController {

    @GetMapping("/")
    public Map<String, String> home() {
        return Map.of(
                "service", "api-gateway",
                "apiDocs", "/docs",
                "health", "/actuator/health",
                "products", "/api/products");
    }
}
