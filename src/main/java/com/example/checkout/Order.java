package com.example.checkout;

import java.math.BigDecimal;

public record Order(String id, String customerEmail, String fulfillmentStatus,
                    BigDecimal total, String currency) {}
