package com.enterprise.order.seller;

import com.enterprise.order.common.PageResponse;
import com.enterprise.order.order.InvoiceService;
import com.enterprise.order.order.InvoiceService.Document;
import com.enterprise.order.order.OrderDtos.CancelRequest;
import com.enterprise.order.order.OrderDtos.OrderResponse;
import com.enterprise.order.order.OrderDtos.SellerOrderResponse;
import com.enterprise.order.order.OrderDtos.ShipRequest;
import com.enterprise.order.order.OrderDtos.StatusUpdateRequest;
import com.enterprise.order.order.OrderFilter;
import com.enterprise.order.order.OrderReports;
import com.enterprise.order.order.OrderQueryService;
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

/** The seller's order desk: only orders of the seller's own store. */
@RestController
@RequestMapping("/api/seller/orders")
@PreAuthorize("hasRole('SELLER')")
@Tag(name = "Seller - orders")
public class SellerOrderController {

    private final OrderService orderService;
    private final OrderQueryService queries;
    private final InvoiceService invoices;

    public SellerOrderController(OrderService orderService, OrderQueryService queries, InvoiceService invoices) {
        this.orderService = orderService;
        this.queries = queries;
        this.invoices = invoices;
    }

    @GetMapping
    @Operation(summary = "Orders of my store with filters (status, dates, payment, amount, region, free text) and sorting")
    public PageResponse<SellerOrderResponse> list(@RequestParam(required = false) List<OrderStatus> status,
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
                                                  @RequestParam(defaultValue = "20") int size,
                                                  @AuthenticationPrincipal AuthenticatedUser seller) {
        OrderFilter filter = new OrderFilter(seller.id(), null, status, paymentState, paymentMethod, from, to, minTotal,
                maxTotal, q, region);
        return queries.search(filter, sort, page, size);
    }

    @GetMapping("/counts")
    @Operation(summary = "Number of orders per status (tabs: awaiting payment, to ship, shipped...)")
    public Map<String, Long> counts(@AuthenticationPrincipal AuthenticatedUser seller) {
        return queries.statusCounts(seller.id());
    }

    @GetMapping("/export/{format}")
    @Operation(summary = "Export the filtered orders as xlsx or pdf")
    public ResponseEntity<byte[]> export(@PathVariable String format,
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
                                         @AuthenticationPrincipal AuthenticatedUser seller) {
        OrderFilter filter = new OrderFilter(seller.id(), null, status, paymentState, paymentMethod, from, to, minTotal,
                maxTotal, q, region);
        return ReportFiles.download(OrderReports.orders("Orders - " + seller.displayName(), filter,
                queries.export(filter, sort), false), format, "orders-" + LocalDate.now());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Order with customer contact, delivery address, costs, profit and history")
    public SellerOrderResponse get(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser seller) {
        return queries.getForSeller(seller, id);
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "Move an order to SHIPPED (carrier + tracking), DELIVERED or CANCELLED")
    public SellerOrderResponse updateStatus(@PathVariable Long id, @Valid @RequestBody StatusUpdateRequest request,
                                            @AuthenticationPrincipal AuthenticatedUser seller) {
        return orderService.updateStatus(seller, id, request);
    }

    @PutMapping("/{id}/ship")
    @Operation(summary = "Ship a confirmed order with carrier and tracking number")
    public SellerOrderResponse ship(@PathVariable Long id, @Valid @RequestBody ShipRequest request,
                                    @AuthenticationPrincipal AuthenticatedUser seller) {
        return orderService.ship(seller, id, request.carrier(), request.trackingNumber(), request.note());
    }

    @PutMapping("/{id}/deliver")
    @Operation(summary = "Mark a shipped order as delivered (cash on delivery is collected)")
    public SellerOrderResponse deliver(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser seller) {
        return orderService.deliver(seller, id);
    }

    @PutMapping("/{id}/cancel")
    @Operation(summary = "Cancel an order before shipping (refund + stock back to inventory)")
    public OrderResponse cancel(@PathVariable Long id, @Valid @RequestBody(required = false) CancelRequest request,
                                @AuthenticationPrincipal AuthenticatedUser seller) {
        return orderService.cancel(seller, id, request == null ? null : request.reason());
    }

    @GetMapping("/{id}/invoice")
    @Operation(summary = "Tax invoice (PDF)")
    public ResponseEntity<byte[]> invoice(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser seller) {
        Document document = invoices.invoice(seller, id);
        return ReportFiles.pdf(document.fileName(), document.content());
    }

    @GetMapping("/{id}/packing-slip")
    @Operation(summary = "Packing slip with pick list and delivery address (PDF)")
    public ResponseEntity<byte[]> packingSlip(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser seller) {
        Document document = invoices.packingSlip(seller, id);
        return ReportFiles.pdf(document.fileName(), document.content());
    }
}
