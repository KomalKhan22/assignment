package com.example.assignment.controllers;

import com.example.assignment.dtos.CreateWalletRequest;
import com.example.assignment.dtos.WalletResponse;
import com.example.assignment.services.WalletService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/wallets")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;

    @PostMapping
    public ResponseEntity<WalletResponse> createWallet(
            @Valid @RequestBody CreateWalletRequest request) {

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(walletService.createWallet(request));
    }

    @GetMapping("/{walletId}")
    public ResponseEntity<WalletResponse> getWallet(@PathVariable UUID walletId) {
        return ResponseEntity.ok(walletService.getWallet(walletId));
    }
}
