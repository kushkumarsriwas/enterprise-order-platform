package com.enterprise.payment.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Reads sales and cost of goods sold from the order service (the caller's token is relayed). */
@Component
public class OrderClient {

    private static final String NAME = "Order service";

    private final RestClient client;

    public OrderClient(ServiceClientFactory factory, @Value("${clients.order.url}") String url) {
        this.client = factory.create(NAME, url);
    }

    public SalesSummary salesSummary(Long merchantId, LocalDate from, LocalDate to) {
        return RemoteCalls.call(NAME, () -> client.get()
                .uri(uri -> uri.path("/internal/merchants/{merchantId}/sales-summary")
                        .queryParam("from", from)
                        .queryParam("to", to)
                        .build(merchantId))
                .retrieve()
                .body(SalesSummary.class));
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SalesSummary(BigDecimal revenue, BigDecimal costOfGoodsSold, BigDecimal grossProfit, long orders,
                               List<MonthAmounts> months) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MonthAmounts(String month, BigDecimal revenue, BigDecimal costOfGoodsSold, long orders) {
    }
}
