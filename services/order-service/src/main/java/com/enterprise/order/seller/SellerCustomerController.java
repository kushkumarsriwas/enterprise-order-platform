package com.enterprise.order.seller;

import com.enterprise.order.reporting.ReportFiles;
import com.enterprise.order.security.AuthenticatedUser;
import com.enterprise.order.seller.CustomerInsightsService.CustomerPage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/seller/customers")
@PreAuthorize("hasRole('SELLER')")
@Tag(name = "Seller - customers")
public class SellerCustomerController {

    private final CustomerInsightsService insights;

    public SellerCustomerController(CustomerInsightsService insights) {
        this.insights = insights;
    }

    @GetMapping
    @Operation(summary = "My customers: orders, spend, recency and segment (NEW, REPEAT, VIP, AT_RISK)")
    public CustomerPage list(@RequestParam(defaultValue = "") String q,
                             @RequestParam(required = false) String segment,
                             @RequestParam(defaultValue = "spent") String sort,
                             @RequestParam(defaultValue = "0") int page,
                             @RequestParam(defaultValue = "20") int size,
                             @AuthenticationPrincipal AuthenticatedUser seller) {
        return insights.customers(seller.id(), q, segment, sort, page, size);
    }

    @GetMapping("/export/{format}")
    @Operation(summary = "Customer list as xlsx or pdf")
    public ResponseEntity<byte[]> export(@PathVariable String format,
                                         @RequestParam(defaultValue = "") String q,
                                         @RequestParam(required = false) String segment,
                                         @RequestParam(defaultValue = "spent") String sort,
                                         @AuthenticationPrincipal AuthenticatedUser seller) {
        return ReportFiles.download(insights.export(seller.id(), seller.displayName(), q, segment, sort), format,
                "customers-" + LocalDate.now());
    }
}
