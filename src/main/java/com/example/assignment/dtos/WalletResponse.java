package com.example.assignment.dtos;

import java.math.BigDecimal;
import java.util.UUID;

public record WalletResponse(
        UUID id,
        String customerId,
        String currency,
        BigDecimal balance
) {
}
