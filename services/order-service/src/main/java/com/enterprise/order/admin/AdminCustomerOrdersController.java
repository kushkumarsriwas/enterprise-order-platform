package com.enterprise.order.admin;

import com.enterprise.order.order.AnalyticsQueries;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** Purchase summary of one customer, shown on the administrator's customer profile. */
@RestController
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin - customers")
public class AdminCustomerOrdersController {

    private final AnalyticsQueries analyticsQueries;

    public AdminCustomerOrdersController(AnalyticsQueries analyticsQueries) {
        this.analyticsQueries = analyticsQueries;
    }

    @GetMapping("/api/admin/customers/{customerId}/purchases")
    @Operation(summary = "Orders, amount spent, first and last order of a customer")
    public AnalyticsQueries.CustomerPurchases purchases(@PathVariable Long customerId) {
        return analyticsQueries.customerPurchases(customerId);
    }
}
