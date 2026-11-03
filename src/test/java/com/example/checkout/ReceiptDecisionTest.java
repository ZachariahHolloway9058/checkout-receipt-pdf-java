package com.example.checkout;

import java.math.BigDecimal;

public final class ReceiptDecisionTest {
    public static void main(String[] args) {
        ReceiptService service = new ReceiptService();
        Order paid = new Order("order-1", "a@example.test", "fulfilled", new BigDecimal("12.00"), "USD");
        Order pending = new Order("order-2", "a@example.test", "pending", new BigDecimal("12.00"), "USD");
        check(service.decide(paid) == ReceiptDecision.READY, "fulfilled paid order is ready");
        check(service.decide(pending) == ReceiptDecision.HOLD, "pending order is held");
        System.out.println("receipt decision checks passed");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
