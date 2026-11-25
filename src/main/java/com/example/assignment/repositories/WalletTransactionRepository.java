package com.example.assignment.repositories;

import com.example.assignment.entities.WalletTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, UUID> {
    @Query("""
        SELECT t
        FROM WalletTransaction t
        WHERE t.sourceWallet.id = :walletId
           OR t.destinationWallet.id = :walletId
        ORDER BY t.createdAt DESC, t.id DESC
    """)
    Page<WalletTransaction> findWalletHistory(
            @Param("walletId") UUID walletId,
            Pageable pageable
    );

    Optional<WalletTransaction> findByIdempotencyKey(String idempotencyKey);
}
