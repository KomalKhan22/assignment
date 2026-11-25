package com.example.assignment.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateWalletRequest(
        @NotBlank @Size(max = 100) String customerId,
        @NotBlank @Pattern(regexp = "^[A-Za-z]{3}$", message = "currency must be a 3-letter code") String currency
) {
}
