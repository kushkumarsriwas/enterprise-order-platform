package com.enterprise.order.config;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HomeController {

    @GetMapping("/")
    public Map<String, String> home() {
        return Map.of("service", "order-service", "docs", "/swagger-ui.html", "health", "/actuator/health");
    }
}
