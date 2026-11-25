package com.example.assignment.services;

import com.example.assignment.common.enums.TransactionType;
import com.example.assignment.common.exceptions.IdempotencyConflictException;
import com.example.assignment.common.exceptions.InsufficientBalanceException;
import com.example.assignment.common.exceptions.InvalidTransferException;
import com.example.assignment.common.exceptions.ResourceNotFoundException;
import com.example.assignment.dtos.DepositRequest;
import com.example.assignment.dtos.DepositResponse;
import com.example.assignment.dtos.TransactionHistoryResponse;
import com.example.assignment.dtos.TransferRequest;
import com.example.assignment.dtos.TransferResponse;
import com.example.assignment.entities.Wallet;
import com.example.assignment.entities.WalletTransaction;
import com.example.assignment.repositories.WalletRepository;
import com.example.assignment.repositories.WalletTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WalletTransactionService {

    private final WalletRepository walletRepository;

    private final WalletTransactionRepository transactionRepository;

    @Transactional
    public DepositResponse deposit(UUID walletId, DepositRequest request) {

        if (request == null || request.amount() == null
                || request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Deposit amount must be positive");
        }

        // Row lock: without it two concurrent deposits can overwrite each other (lost update).
        Wallet wallet = lockWallet(walletId);

        if (request.currency() != null && !request.currency().isBlank()
                && !wallet.getCurrency().equalsIgnoreCase(request.currency())) {
            throw new IllegalArgumentException("Deposit currency does not match wallet currency");
        }

        wallet.setBalance(wallet.getBalance().add(request.amount()));

        WalletTransaction transaction = new WalletTransaction();
        transaction.setAmount(request.amount());
        transaction.setTransactionType(TransactionType.DEPOSIT);
        transaction.setCurrency(wallet.getCurrency());
        transaction.setDestinationWallet(wallet);
        transaction.setSourceWallet(wallet);
        transaction.setCreatedAt(Instant.now());

        transactionRepository.save(transaction);

        return new DepositResponse(
                wallet.getId(),
                request.amount(),
                wallet.getBalance()
        );
    }

    @Transactional
    public TransferResponse transferMoney(TransferRequest request, String idempotencyKey) {

        UUID sourceId = request.sourceWalletId();
        UUID destinationId = request.destinationWalletId();
        BigDecimal amount = request.amount();

        // 1. Cheap validation first - no DB access for obviously bad requests.
        if (sourceId == null || destinationId == null) {
            throw new InvalidTransferException("Source and destination wallet IDs are required");
        }
        if (sourceId.equals(destinationId)) {
            throw new InvalidTransferException("Source and destination wallets must be different");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidTransferException("Transfer amount must be greater than zero");
        }

        String key = (idempotencyKey == null || idempotencyKey.isBlank()) ? null : idempotencyKey.trim();

        // 2. Lock both wallets in a consistent (ascending id) order to avoid deadlocks.
        UUID firstId = sourceId.compareTo(destinationId) < 0 ? sourceId : destinationId;
        UUID secondId = firstId.equals(sourceId) ? destinationId : sourceId;

        Wallet first = lockWallet(firstId);
        Wallet second = lockWallet(secondId);

        Wallet source = first.getId().equals(sourceId) ? first : second;
        Wallet destination = (source == first) ? second : first;

        // 3. Idempotency check AFTER the locks: a concurrent request with the same key
        //    has either committed already (we see it) or is still holding the lock (we wait).
        if (key != null) {
            Optional<WalletTransaction> existing = transactionRepository.findByIdempotencyKey(key);
            if (existing.isPresent()) {
                WalletTransaction previous = existing.get();
                if (!isSameRequest(previous, sourceId, destinationId, amount)) {
                    throw new IdempotencyConflictException(
                            "Idempotency key was already used with a different request");
                }
                return mapToTransferResponse(previous);
            }
        }

        // 4. Business rules
        if (!source.getCurrency().equals(destination.getCurrency())) {
            throw new InvalidTransferException("Both wallets must have the same currency");
        }

        if (source.getBalance().compareTo(amount) < 0) {
            throw new InsufficientBalanceException("Insufficient wallet balance");
        }

        // 5. Debit and credit (committed together or not at all)
        source.setBalance(source.getBalance().subtract(amount));
        destination.setBalance(destination.getBalance().add(amount));

        // 6. Record the transfer
        WalletTransaction transaction = new WalletTransaction();
        transaction.setSourceWallet(source);
        transaction.setDestinationWallet(destination);
        transaction.setAmount(amount);
        transaction.setCurrency(source.getCurrency());
        transaction.setTransactionType(TransactionType.TRANSFER);
        transaction.setIdempotencyKey(key);
        transaction.setCreatedAt(Instant.now());

        WalletTransaction savedTransaction = transactionRepository.save(transaction);

        return mapToTransferResponse(savedTransaction);
    }

    @Transactional(readOnly = true)
    public Page<TransactionHistoryResponse> getTransactionHistory(UUID walletId, Pageable pageable) {

        if (!walletRepository.existsById(walletId)) {
            throw new ResourceNotFoundException("Wallet not found: " + walletId);
        }

        return transactionRepository
                .findWalletHistory(walletId, pageable)
                .map(transaction -> new TransactionHistoryResponse(
                        transaction.getId(),
                        transaction.getAmount(),
                        directionFor(transaction, walletId),
                        transaction.getTransactionType().toString(),
                        transaction.getCreatedAt()
                ));
    }

    private Wallet lockWallet(UUID walletId) {
        return walletRepository.findByIdForUpdate(walletId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet not found: " + walletId));
    }

    private boolean isSameRequest(WalletTransaction previous, UUID sourceId, UUID destinationId, BigDecimal amount) {
        return previous.getSourceWallet().getId().equals(sourceId)
                && previous.getDestinationWallet().getId().equals(destinationId)
                && previous.getAmount().compareTo(amount) == 0;
    }

    private String directionFor(WalletTransaction transaction, UUID walletId) {
        // A deposit has source == destination; from the wallet's point of view it is money coming in.
        if (transaction.getTransactionType() == TransactionType.DEPOSIT) {
            return "INCOMING";
        }
        return transaction.getSourceWallet().getId().equals(walletId) ? "OUTGOING" : "INCOMING";
    }

    private TransferResponse mapToTransferResponse(WalletTransaction transaction) {
        return new TransferResponse(
                transaction.getId(),
                transaction.getSourceWallet().getId(),
                transaction.getDestinationWallet().getId(),
                transaction.getIdempotencyKey(),
                transaction.getAmount(),
                transaction.getTransactionType().name(),
                transaction.getCreatedAt()
        );
    }
}
