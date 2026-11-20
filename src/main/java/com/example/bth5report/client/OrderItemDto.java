package com.example.bth5report.client;
import java.math.BigDecimal;
public record OrderItemDto(Long productId, String productName, BigDecimal unitPrice, Integer quantity, BigDecimal subtotal) {}
