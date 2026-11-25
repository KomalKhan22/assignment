package com.example.assignment.controllers;

import com.example.assignment.dtos.DepositRequest;
import com.example.assignment.dtos.DepositResponse;
import com.example.assignment.dtos.TransactionHistoryResponse;
import com.example.assignment.dtos.TransferRequest;
import com.example.assignment.dtos.TransferResponse;
import com.example.assignment.services.WalletTransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class WalletTransactionController {

    private final WalletTransactionService walletTransactionService;

    // Not part of the assignment spec, but wallets start at zero so something has to fund them.
    @PostMapping("/wallets/{walletId}/deposit")
    public ResponseEntity<DepositResponse> depositMoney(
            @PathVariable UUID walletId,
            @Valid @RequestBody DepositRequest request) {

        return ResponseEntity.ok(walletTransactionService.deposit(walletId, request));
    }

    @PostMapping("/transfers")
    public ResponseEntity<TransferResponse> transferMoney(
            @RequestHeader(value = "Idempotency-Key", required = true) String idempotencyKey,
            @Valid @RequestBody TransferRequest request) {

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(walletTransactionService.transferMoney(request, idempotencyKey));
    }

    @GetMapping("/wallets/{walletId}/transactions")
    public Page<TransactionHistoryResponse> getHistory(
            @PathVariable UUID walletId,
            @PageableDefault(size = 20) Pageable pageable) {

        return walletTransactionService.getTransactionHistory(walletId, pageable);
    }
}
