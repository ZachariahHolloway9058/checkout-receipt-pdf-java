package com.example.checkout;

import java.math.BigDecimal;
import java.util.Map;

public final class ReceiptService {
    public ReceiptDecision decide(Order order) {
        if ("fulfilled".equals(order.fulfillmentStatus())
                && order.total().compareTo(BigDecimal.ZERO) > 0) {
            return ReceiptDecision.READY;
        }
        return ReceiptDecision.HOLD;
    }

    public String receiptKey(Order order) {
        return "receipts/" + order.id() + ".pdf";
    }

    public Map<String, String> fields(Order order) {
        return Map.of(
                "order_id", order.id(),
                "customer_email", order.customerEmail(),
                "fulfillment_status", order.fulfillmentStatus(),
                "total", order.total().toPlainString(),
                "currency", order.currency());
    }
}
