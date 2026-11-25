package com.example.assignment.dtos;

import java.math.BigDecimal;
import java.util.UUID;

public record DepositResponse(
        UUID walletId,
        BigDecimal depositedAmount,
        BigDecimal currentBalance
) {}