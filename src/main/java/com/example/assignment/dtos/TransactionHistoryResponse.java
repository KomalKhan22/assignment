package com.example.assignment.dtos;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransactionHistoryResponse(
        UUID transactionId,
        BigDecimal amount,
        String direction,
        String transactionType,
        Instant timestamp
) {
}
