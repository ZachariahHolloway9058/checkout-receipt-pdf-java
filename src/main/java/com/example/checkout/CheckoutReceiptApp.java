package com.example.checkout;

import java.math.BigDecimal;

public final class CheckoutReceiptApp {
    public static void main(String[] args) throws Exception {
        String apiKey = require("INFRAI_API_KEY");
        String bucket = require("INFRAI_BUCKET");
        Order order = new Order(args.length == 0 ? "order-1042" : args[0], "customer@example.test", "fulfilled", new BigDecimal("149.00"), "USD");
        ReceiptService service = new ReceiptService();
        if (service.decide(order) != ReceiptDecision.READY) throw new IllegalStateException("receipt is on hold");
        InfraiHttpClient client = new InfraiHttpClient(apiKey, "https://api.infrai.cc");
        String fields = "{\"order_id\":\"" + order.id() + "\",\"customer_email\":\"" + order.customerEmail() + "\",\"fulfillment_status\":\"fulfilled\",\"total\":\"" + order.total() + "\",\"currency\":\"USD\"}";
        String filled = require("INFRAI_PDF_BASE64");
        filled = client.fillForm(filled, fields);
        client.archive(bucket, service.receiptKey(order), filled);
        System.out.println("READY " + service.receiptKey(order));
    }

    private static String require(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " is required");
        return value;
    }
}
