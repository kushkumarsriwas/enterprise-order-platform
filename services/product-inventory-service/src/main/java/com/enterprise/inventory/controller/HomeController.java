package com.enterprise.inventory.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HomeController {
    @GetMapping("/")
    public String home() {
        return "Enterprise Product Inventory API is running. Visit /swagger-ui/index.html for API documentation.";
    }
}
