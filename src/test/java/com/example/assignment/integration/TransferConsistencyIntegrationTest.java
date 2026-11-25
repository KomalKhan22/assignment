
package com.example.assignment.integration;

import com.example.assignment.dtos.CreateWalletRequest;
import com.example.assignment.dtos.DepositRequest;
import com.example.assignment.dtos.TransferRequest;
import com.example.assignment.entities.Wallet;
import com.example.assignment.entities.WalletTransaction;
import com.example.assignment.repositories.WalletRepository;
import com.example.assignment.repositories.WalletTransactionRepository;
import com.example.assignment.services.WalletService;
import com.example.assignment.services.WalletTransactionService;
import com.example.assignment.common.exceptions.InsufficientBalanceException;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
class TransferConsistencyIntegrationTest {

    @Autowired
    private WalletService walletService;

    @Autowired
    private WalletTransactionService transactionService;

    @Autowired
    private WalletRepository walletRepository;

    @MockitoSpyBean
    private WalletTransactionRepository transactionRepository;

    private UUID newWallet(String currency, String initialBalance) {
        UUID id = walletService.createWallet(
                new CreateWalletRequest(
                        "CUST-" + UUID.randomUUID(), currency)).id();

        if (new BigDecimal(initialBalance).signum() > 0) {
            transactionService.deposit(
                    id, new DepositRequest(new BigDecimal(initialBalance), currency));
        }

        return id;
    }

    private BigDecimal balanceOf(UUID walletId) {
        return walletRepository.findById(walletId)
                .orElseThrow().getBalance();
    }

    private long historySize(UUID walletId) {
        return transactionRepository.findWalletHistory(
                walletId, PageRequest.of(0, 1)).getTotalElements();
    }

    private static TransferRequest transfer(
            UUID from, UUID to, String amount) {
        return new TransferRequest(from, to, new BigDecimal(amount));
    }

    private <T> List<T> runConcurrently(List<Callable<T>> tasks)
            throws Exception {

        ExecutorService pool =
                Executors.newFixedThreadPool(tasks.size());

        try {
            CountDownLatch ready = new CountDownLatch(tasks.size());
            CountDownLatch start = new CountDownLatch(1);

            List<Future<T>> futures = new ArrayList<>();

            for (Callable<T> task : tasks) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    start.await();
                    return task.call();
                }));
            }

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<T> results = new ArrayList<>();

            for (Future<T> future : futures) {
                results.add(future.get(60, TimeUnit.SECONDS));
            }

            return results;

        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void concurrentTransfersFromOneWallet_neverOverdraw()
            throws Exception {

        UUID source = newWallet("PKR", "100.00");
        UUID destination = newWallet("PKR", "0.00");

        List<Callable<Boolean>> tasks = new ArrayList<>();

        for (int i = 0; i < 8; i++) {
            tasks.add(() -> {
                try {
                    transactionService.transferMoney(
                            transfer(source, destination, "30.00"), null);
                    return true;
                } catch (InsufficientBalanceException e) {
                    return false;
                }
            });
        }

        long succeeded = runConcurrently(tasks)
                .stream().filter(ok -> ok).count();

        assertThat(succeeded).isEqualTo(3);
        assertThat(balanceOf(source)).isEqualByComparingTo("10.00");
        assertThat(balanceOf(destination)).isEqualByComparingTo("90.00");
        assertThat(historySize(source)).isEqualTo(4);
    }

    @Test
    void opposingConcurrentTransfers_doNotDeadlock_andConserveMoney()
            throws Exception {

        UUID a = newWallet("PKR", "1000.00");
        UUID b = newWallet("PKR", "1000.00");

        List<Callable<Boolean>> tasks = new ArrayList<>();

        for (int i = 0; i < 5; i++) {
            tasks.add(() -> {
                transactionService.transferMoney(
                        transfer(a, b, "10.00"), null);
                return true;
            });

            tasks.add(() -> {
                transactionService.transferMoney(
                        transfer(b, a, "10.00"), null);
                return true;
            });
        }

        runConcurrently(tasks);

        assertThat(balanceOf(a)).isEqualByComparingTo("1000.00");
        assertThat(balanceOf(b)).isEqualByComparingTo("1000.00");
    }

    @Test
    void failureDuringTransfer_rollsBackDebitCreditAndTransactionRecord() {

        UUID source = newWallet("PKR", "100.00");
        UUID destination = newWallet("PKR", "0.00");

        long sourceHistoryBefore = historySize(source);

        doThrow(new IllegalStateException("db failure"))
                .when(transactionRepository)
                .save(any(WalletTransaction.class));

        assertThatThrownBy(() ->
                transactionService.transferMoney(
                        transfer(source, destination, "40.00"), null))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("db failure");

        assertThat(balanceOf(source))
                .isEqualByComparingTo("100.00");

        assertThat(balanceOf(destination))
                .isEqualByComparingTo("0.00");

        assertThat(historySize(source))
                .isEqualTo(sourceHistoryBefore);

        assertThat(historySize(destination))
                .isZero();
    }
}
