package com.example.assignment.services;

import com.example.assignment.dtos.CreateWalletRequest;
import com.example.assignment.dtos.WalletResponse;
import com.example.assignment.entities.Wallet;
import com.example.assignment.repositories.WalletRepository;
import lombok.RequiredArgsConstructor;
import com.example.assignment.common.exceptions.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;


@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletRepository walletRepository;

    @Transactional
    public WalletResponse createWallet(CreateWalletRequest request) {

        Wallet wallet = new Wallet();

        wallet.setCustomerId(request.customerId());
        wallet.setCurrency(request.currency().toUpperCase());
        wallet.setBalance(BigDecimal.ZERO);

        Wallet savedWallet = walletRepository.save(wallet);

        return mapToResponse(savedWallet);
    }

    @Transactional(readOnly = true)
    public WalletResponse getWallet(UUID walletId) {

        Wallet wallet = walletRepository.findById(walletId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Wallet not found: " + walletId
                        )
                );

        return mapToResponse(wallet);
    }

    private WalletResponse mapToResponse(Wallet wallet) {

        return new WalletResponse(
                wallet.getId(),
                wallet.getCustomerId(),
                wallet.getCurrency(),
                wallet.getBalance()
        );
    }
}


