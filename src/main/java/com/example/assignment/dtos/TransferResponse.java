package com.example.assignment.dtos;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransferResponse(
        UUID transactionId,
        UUID sourceWalletId,
        UUID destinationWalletId,
        String idempotencyKey,
        BigDecimal amount,
        String transactionType,
        Instant createdAt
) {
}
