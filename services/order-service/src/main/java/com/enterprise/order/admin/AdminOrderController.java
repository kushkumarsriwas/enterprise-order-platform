package com.enterprise.order.admin;

import com.enterprise.order.common.PageResponse;
import com.enterprise.order.order.InvoiceService;
import com.enterprise.order.order.InvoiceService.Document;
import com.enterprise.order.order.OrderDtos.CancelRequest;
import com.enterprise.order.order.OrderDtos.OrderResponse;
import com.enterprise.order.order.OrderDtos.SellerOrderResponse;
import com.enterprise.order.order.OrderFilter;
import com.enterprise.order.order.OrderQueryService;
import com.enterprise.order.order.OrderReports;
import com.enterprise.order.order.OrderService;
import com.enterprise.order.order.OrderStatus;
import com.enterprise.order.order.PaymentState;
import com.enterprise.order.reporting.ReportFiles;
import com.enterprise.order.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Nexora Control Center: every order of every store, with both parties' details, costs and profit. */
@RestController
@RequestMapping("/api/admin/orders")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin - orders")
public class AdminOrderController {

    private final OrderService orderService;
    private final OrderQueryService queries;
    private final InvoiceService invoices;

    public AdminOrderController(OrderService orderService, OrderQueryService queries, InvoiceService invoices) {
        this.orderService = orderService;
        this.queries = queries;
        this.invoices = invoices;
    }

    @GetMapping
    @Operation(summary = "All orders with filters: store, customer, status, payment, dates, amount, region, text")
    public PageResponse<SellerOrderResponse> list(@RequestParam(required = false) Long sellerId,
                                                  @RequestParam(required = false) Long customerId,
                                                  @RequestParam(required = false) List<OrderStatus> status,
                                                  @RequestParam(required = false) PaymentState paymentState,
                                                  @RequestParam(required = false) String paymentMethod,
                                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                  @RequestParam(required = false) BigDecimal minTotal,
                                                  @RequestParam(required = false) BigDecimal maxTotal,
                                                  @RequestParam(required = false) String region,
                                                  @RequestParam(required = false) String q,
                                                  @RequestParam(defaultValue = "newest") String sort,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "25") int size) {
        return queries.search(new OrderFilter(sellerId, customerId, status, paymentState, paymentMethod, from, to,
                minTotal, maxTotal, q, region), sort, page, size);
    }

    @GetMapping("/counts")
    @Operation(summary = "Orders per status on the whole marketplace (or one store)")
    public Map<String, Long> counts(@RequestParam(required = false) Long sellerId) {
        return queries.statusCounts(sellerId);
    }

    @GetMapping("/export/{format}")
    @Operation(summary = "Export the filtered orders as xlsx or pdf")
    public ResponseEntity<byte[]> export(@PathVariable String format,
                                         @RequestParam(required = false) Long sellerId,
                                         @RequestParam(required = false) Long customerId,
                                         @RequestParam(required = false) List<OrderStatus> status,
                                         @RequestParam(required = false) PaymentState paymentState,
                                         @RequestParam(required = false) String paymentMethod,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                         @RequestParam(required = false) BigDecimal minTotal,
                                         @RequestParam(required = false) BigDecimal maxTotal,
                                         @RequestParam(required = false) String region,
                                         @RequestParam(required = false) String q,
                                         @RequestParam(defaultValue = "newest") String sort) {
        OrderFilter filter = new OrderFilter(sellerId, customerId, status, paymentState, paymentMethod, from, to,
                minTotal, maxTotal, q, region);
        return ReportFiles.download(OrderReports.orders("Marketplace orders", filter, queries.export(filter, sort), true),
                format, "nexora-orders-" + LocalDate.now());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Full order: customer and store, delivery address, items with cost and profit, history")
    public SellerOrderResponse get(@PathVariable Long id) {
        return queries.getWithDetails(id);
    }

    @PutMapping("/{id}/cancel")
    @Operation(summary = "Cancel an order on behalf of support (refund + stock back to the seller)")
    public OrderResponse cancel(@PathVariable Long id, @Valid @RequestBody(required = false) CancelRequest request,
                                @AuthenticationPrincipal AuthenticatedUser admin) {
        return orderService.cancel(admin, id, request == null ? null : request.reason());
    }

    @GetMapping("/{id}/invoice")
    @Operation(summary = "Tax invoice of any order (PDF)")
    public ResponseEntity<byte[]> invoice(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser admin) {
        Document document = invoices.invoice(admin, id);
        return ReportFiles.pdf(document.fileName(), document.content());
    }
}
