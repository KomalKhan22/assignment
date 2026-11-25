package com.example.assignment.services;

import com.example.assignment.common.enums.TransactionType;
import com.example.assignment.common.exceptions.IdempotencyConflictException;
import com.example.assignment.common.exceptions.InsufficientBalanceException;
import com.example.assignment.common.exceptions.InvalidTransferException;
import com.example.assignment.common.exceptions.ResourceNotFoundException;
import com.example.assignment.dtos.DepositRequest;
import com.example.assignment.dtos.TransferRequest;
import com.example.assignment.dtos.TransferResponse;
import com.example.assignment.entities.Wallet;
import com.example.assignment.entities.WalletTransaction;
import com.example.assignment.repositories.WalletRepository;
import com.example.assignment.repositories.WalletTransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WalletTransactionServiceTest {

    @Mock
    private WalletRepository walletRepository;
    @Mock
    private WalletTransactionRepository transactionRepository;

    @InjectMocks
    private WalletTransactionService service;

    // ---------------------------------------------------------------- helpers

    private static Wallet wallet(UUID id, String currency, String balance) {
        Wallet wallet = new Wallet();
        wallet.setId(id);
        wallet.setCustomerId("CUST-" + id);
        wallet.setCurrency(currency);
        wallet.setBalance(new BigDecimal(balance));
        return wallet;
    }

    private void locks(Wallet wallet) {
        lenient().when(walletRepository.findByIdForUpdate(wallet.getId())).thenReturn(Optional.of(wallet));
    }

    private void saveAssignsId() {
        when(transactionRepository.save(any(WalletTransaction.class))).thenAnswer(invocation -> {
            WalletTransaction transaction = invocation.getArgument(0);
            transaction.setId(UUID.randomUUID());
            return transaction;
        });
    }

    private static TransferRequest transfer(UUID from, UUID to, String amount) {
        return new TransferRequest(from, to, new BigDecimal(amount));
    }

    // --------------------------------------------------------------- transfer

    @Test
    void transfer_movesMoney_andRecordsTransaction() {
        UUID srcId = UUID.randomUUID();
        UUID dstId = UUID.randomUUID();
        Wallet src = wallet(srcId, "PKR", "1000.00");
        Wallet dst = wallet(dstId, "PKR", "50.00");
        locks(src);
        locks(dst);
        saveAssignsId();

        TransferResponse response = service.transferMoney(transfer(srcId, dstId, "500.00"), "key-1");

        assertThat(src.getBalance()).isEqualByComparingTo("500.00");
        assertThat(dst.getBalance()).isEqualByComparingTo("550.00");
        assertThat(response.sourceWalletId()).isEqualTo(srcId);
        assertThat(response.destinationWalletId()).isEqualTo(dstId);
        verify(transactionRepository).save(any(WalletTransaction.class));
    }

    @Test
    void transfer_insufficientBalance_throws_andChangesNothing() {
        UUID srcId = UUID.randomUUID();
        UUID dstId = UUID.randomUUID();
        Wallet src = wallet(srcId, "PKR", "100.00");
        Wallet dst = wallet(dstId, "PKR", "0.00");
        locks(src);
        locks(dst);

        assertThatThrownBy(() -> service.transferMoney(transfer(srcId, dstId, "100.01"), null))
                .isInstanceOf(InsufficientBalanceException.class);

        assertThat(src.getBalance()).isEqualByComparingTo("100.00");
        assertThat(dst.getBalance()).isEqualByComparingTo("0.00");
        verify(transactionRepository, never()).save(any(WalletTransaction.class));
    }

    @Test
    void transfer_nonPositiveAmount_isRejected_beforeTouchingTheDatabase() {
        assertThatThrownBy(() -> service.transferMoney(transfer(UUID.randomUUID(), UUID.randomUUID(), "0"), null))
                .isInstanceOf(InvalidTransferException.class);

        verifyNoInteractions(walletRepository, transactionRepository);
    }

    @Test
    void transfer_sameSourceAndDestination_isRejected() {
        UUID id = UUID.randomUUID();

        assertThatThrownBy(() -> service.transferMoney(transfer(id, id, "10.00"), null))
                .isInstanceOf(InvalidTransferException.class);

        verifyNoInteractions(walletRepository, transactionRepository);
    }

    @Test
    void transfer_unknownWallet_throwsNotFound() {
        UUID srcId = UUID.randomUUID();
        UUID dstId = UUID.randomUUID();
        locks(wallet(dstId, "PKR", "0.00")); // source is not stubbed -> Optional.empty()

        assertThatThrownBy(() -> service.transferMoney(transfer(srcId, dstId, "10.00"), null))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(transactionRepository, never()).save(any(WalletTransaction.class));
    }

    @Test
    void transfer_currencyMismatch_isRejected() {
        UUID srcId = UUID.randomUUID();
        UUID dstId = UUID.randomUUID();
        Wallet src = wallet(srcId, "PKR", "100.00");
        Wallet dst = wallet(dstId, "USD", "0.00");
        locks(src);
        locks(dst);

        assertThatThrownBy(() -> service.transferMoney(transfer(srcId, dstId, "10.00"), null))
                .isInstanceOf(InvalidTransferException.class);

        assertThat(src.getBalance()).isEqualByComparingTo("100.00");
        verify(transactionRepository, never()).save(any(WalletTransaction.class));
    }

    // ------------------------------------------------------------ idempotency

    private WalletTransaction existingTransfer(Wallet from, Wallet to, String amount) {
        WalletTransaction original = new WalletTransaction();
        original.setId(UUID.randomUUID());
        original.setSourceWallet(from);
        original.setDestinationWallet(to);
        original.setTransactionType(TransactionType.TRANSFER);
        original.setAmount(new BigDecimal(amount));
        original.setCurrency(from.getCurrency());
        original.setIdempotencyKey("key-1");
        original.setCreatedAt(Instant.now());
        return original;
    }

    @Test
    void transfer_replayWithSameKeyAndSameRequest_returnsOriginal_withoutMovingMoneyAgain() {
        UUID srcId = UUID.randomUUID();
        UUID dstId = UUID.randomUUID();
        Wallet src = wallet(srcId, "PKR", "500.00");
        Wallet dst = wallet(dstId, "PKR", "500.00");
        locks(src);
        locks(dst);
        WalletTransaction original = existingTransfer(src, dst, "500.00");
        when(transactionRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.of(original));

        TransferResponse response = service.transferMoney(transfer(srcId, dstId, "500"), "key-1");

        assertThat(response.transactionId()).isEqualTo(original.getId());
        assertThat(src.getBalance()).isEqualByComparingTo("500.00");
        assertThat(dst.getBalance()).isEqualByComparingTo("500.00");
        verify(transactionRepository, never()).save(any(WalletTransaction.class));
    }

    @Test
    void transfer_sameKeyDifferentAmount_isAConflict() {
        UUID srcId = UUID.randomUUID();
        UUID dstId = UUID.randomUUID();
        Wallet src = wallet(srcId, "PKR", "900.00");
        Wallet dst = wallet(dstId, "PKR", "0.00");
        locks(src);
        locks(dst);
        when(transactionRepository.findByIdempotencyKey("key-1"))
                .thenReturn(Optional.of(existingTransfer(src, dst, "500.00")));

        assertThatThrownBy(() -> service.transferMoney(transfer(srcId, dstId, "600.00"), "key-1"))
                .isInstanceOf(IdempotencyConflictException.class);

        assertThat(src.getBalance()).isEqualByComparingTo("900.00");
        verify(transactionRepository, never()).save(any(WalletTransaction.class));
    }

    // ---------------------------------------------------------------- deposit

    @Test
    void deposit_increasesBalance_andRecordsTransaction() {
        UUID id = UUID.randomUUID();
        Wallet wallet = wallet(id, "PKR", "100.00");
        locks(wallet);
        saveAssignsId();

        var response = service.deposit(id, new DepositRequest(new BigDecimal("25.50"), null));

        assertThat(wallet.getBalance()).isEqualByComparingTo("125.50");
        assertThat(response.currentBalance()).isEqualByComparingTo("125.50");
        verify(transactionRepository).save(any(WalletTransaction.class));
    }

    @Test
    void deposit_nonPositiveAmount_isRejected() {
        assertThatThrownBy(() -> service.deposit(UUID.randomUUID(), new DepositRequest(new BigDecimal("-5"), null)))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(walletRepository, transactionRepository);
    }
}
