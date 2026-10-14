package com.enterprise.inventory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ProductInventoryServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProductInventoryServiceApplication.class, args);
    }
}
