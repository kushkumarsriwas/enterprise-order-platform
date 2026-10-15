package com.enterprise.order.order;

import com.enterprise.order.client.StoreDirectoryClient;
import com.enterprise.order.client.StoreDirectoryClient.StoreDetails;
import com.enterprise.order.common.ApiException;
import com.enterprise.order.order.OrderDtos.SellerOrderItemResponse;
import com.enterprise.order.order.OrderDtos.SellerOrderResponse;
import com.enterprise.order.reporting.PdfReportWriter;
import com.enterprise.order.reporting.Report;
import com.enterprise.order.security.AuthenticatedUser;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * UAE tax invoices (prices include 5% VAT; the seller's TRN comes from the account service) and packing slips,
 * rendered as PDF. Customers get the invoice of their own orders, sellers of their sales, the administrator all.
 */
@Service
public class InvoiceService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.US)
            .withZone(ZoneOffset.UTC);

    private final OrderRepository orderRepository;
    private final StoreDirectoryClient stores;
    private final TransactionTemplate tx;

    public InvoiceService(OrderRepository orderRepository, StoreDirectoryClient stores, TransactionTemplate tx) {
        this.orderRepository = orderRepository;
        this.stores = stores;
        this.tx = tx;
    }

    public static String invoiceNumber(String orderNumber) {
        return "INV-" + orderNumber.substring(orderNumber.indexOf('-') + 1);
    }

    public Document invoice(AuthenticatedUser user, Long orderId) {
        SellerOrderResponse order = accessible(user, orderId);
        if (!OrderMapper.isInvoiced(OrderStatus.valueOf(order.status()))) {
            throw ApiException.conflict("The tax invoice is issued once order " + order.orderNumber()
                    + " is confirmed (current status: " + order.status() + ")");
        }
        StoreDetails store = stores.find(order.sellerId());
        return new Document(invoiceNumber(order.orderNumber()) + ".pdf", PdfReportWriter.write(invoiceReport(order, store)));
    }

    public Document packingSlip(AuthenticatedUser user, Long orderId) {
        if (user.isCustomer()) {
            throw ApiException.forbidden("Packing slips are available to the seller");
        }
        SellerOrderResponse order = accessible(user, orderId);
        if (OrderStatus.valueOf(order.status()) == OrderStatus.PENDING
                || OrderStatus.valueOf(order.status()) == OrderStatus.CANCELLED) {
            throw ApiException.conflict("Order " + order.orderNumber() + " is " + order.status()
                    + " - there is nothing to pack");
        }
        StoreDetails store = stores.find(order.sellerId());
        return new Document("packing-slip-" + order.orderNumber() + ".pdf",
                PdfReportWriter.write(packingSlipReport(order, store)));
    }

    static Report invoiceReport(SellerOrderResponse order, StoreDetails store) {
        Instant issued = order.confirmedAt() != null ? order.confirmedAt() : order.createdAt();
        List<List<Object>> rows = new ArrayList<>();
        BigDecimal net = BigDecimal.ZERO;
        BigDecimal vat = BigDecimal.ZERO;
        int line = 1;
        for (SellerOrderItemResponse item : order.items()) {
            BigDecimal lineVat = OrderMapper.vatIncluded(item.lineTotal());
            BigDecimal lineNet = item.lineTotal().subtract(lineVat);
            BigDecimal unitNet = item.unitPrice().multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(100).add(OrderMapper.VAT_RATE_PERCENT), 2, RoundingMode.HALF_UP);
            rows.add(List.of(line++, item.productName(), item.sku(), item.quantity(), unitNet, lineNet, lineVat,
                    item.lineTotal()));
            net = net.add(lineNet);
            vat = vat.add(lineVat);
        }
        rows.add(List.of("", "Total (" + order.currency() + ")", "", order.units(), "", net, vat, order.totalAmount()));
        String storeName = store != null && store.storeName() != null ? store.storeName() : order.sellerName();
        List<String> seller = new ArrayList<>();
        seller.add(storeName);
        if (store != null) {
            seller.add(join(store.city(), store.country()));
            seller.add("TRN: " + (blank(store.vatNumber()) ? "not VAT registered" : store.vatNumber()));
            if (!blank(store.tradeLicenseNumber())) {
                seller.add("Trade licence: " + store.tradeLicenseNumber());
            }
            if (!blank(store.supportEmail())) {
                seller.add(store.supportEmail());
            }
        }
        seller.add("Sold on Nexora Marketplace");
        return new Report(
                "Tax Invoice",
                "Invoice " + invoiceNumber(order.orderNumber()) + "   |   Order " + order.orderNumber()
                        + "   |   Date of supply " + DATE.format(issued),
                List.of(new Report.Metric("Total excluding VAT", net),
                        new Report.Metric("VAT 5%", vat),
                        new Report.Metric("Total including VAT (" + order.currency() + ")", order.totalAmount())),
                List.of(new Report.Table("Items",
                        List.of("#", "Description", "SKU", "Qty", "Unit price excl. VAT", "Taxable amount", "VAT 5%",
                                "Amount incl. VAT"),
                        rows)),
                List.of(new Report.Block("Sold by", seller),
                        new Report.Block("Bill to / Ship to", List.of(
                                order.recipientName() == null ? order.customerUsername() : order.recipientName(),
                                order.shippingAddress(),
                                order.customerEmail() == null ? "" : order.customerEmail())),
                        new Report.Block("Payment", List.of(
                                "Method: " + (order.paymentMethod() == null ? "-" : describe(order.paymentMethod())),
                                "Status: " + order.paymentState().replace('_', ' ').toLowerCase(Locale.ROOT),
                                "Order date: " + DATE.format(order.createdAt()),
                                "Prices include 5% UAE VAT"))));
    }

    static Report packingSlipReport(SellerOrderResponse order, StoreDetails store) {
        List<List<Object>> rows = new ArrayList<>();
        int line = 1;
        for (SellerOrderItemResponse item : order.items()) {
            rows.add(List.of(line++, item.productName(), item.sku(), item.quantity(), "[   ]"));
        }
        String storeName = store != null && store.storeName() != null ? store.storeName() : order.sellerName();
        return new Report(
                "Packing Slip",
                "Order " + order.orderNumber() + "   |   " + order.units() + " unit(s)   |   Ordered "
                        + DATE.format(order.createdAt()),
                List.of(),
                List.of(new Report.Table("Pick list", List.of("#", "Product", "SKU", "Qty", "Packed"), rows)),
                List.of(new Report.Block("Ship to", List.of(
                                order.recipientName() == null ? order.customerUsername() : order.recipientName(),
                                order.shippingAddress())),
                        new Report.Block("From", List.of(storeName,
                                store == null ? "" : join(store.city(), store.country()),
                                store == null || blank(store.phone()) ? "" : "Phone: " + store.phone())),
                        new Report.Block("Delivery", List.of(
                                "Payment: " + (order.paymentState().equals("PAY_ON_DELIVERY")
                                        ? "collect " + order.currency() + " " + order.totalAmount() + " in cash"
                                        : "prepaid"),
                                "Carrier: " + (order.carrier() == null ? "-" : order.carrier()),
                                "Tracking: " + (order.trackingNumber() == null ? "-" : order.trackingNumber())))));
    }

    private SellerOrderResponse accessible(AuthenticatedUser user, Long orderId) {
        return tx.execute(status -> {
            Order order = orderRepository.findWithItemsById(orderId)
                    .orElseThrow(() -> ApiException.notFound("Order " + orderId + " not found"));
            boolean allowed = user.isAdmin()
                    || (user.isCustomer() && order.isOwnedBy(user.id()))
                    || (user.isSeller() && order.isSoldBy(user.id()));
            if (!allowed) {
                throw ApiException.notFound("Order " + orderId + " not found");
            }
            return OrderMapper.toSellerResponse(order);
        });
    }

    private static String describe(String method) {
        return switch (method) {
            case "CARD" -> "Card";
            case "UPI" -> "UPI";
            case "COD" -> "Cash on delivery";
            default -> method;
        };
    }

    private static String join(String first, String second) {
        if (blank(first)) {
            return blank(second) ? "" : second;
        }
        return blank(second) ? first : first + ", " + second;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    /** A generated file: name + content. */
    public record Document(String fileName, byte[] content) {
    }
}
