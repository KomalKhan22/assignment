package com.example.assignment.entities;

import com.example.assignment.common.enums.TransactionType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "wallet_transaction",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "wallet_transaction_idempotency_key",
                        columnNames = "idempotency_key"
                )
        },
        indexes = {
                @Index(name = "idx_wt_source_created", columnList = "source_wallet_id, created_at"),
                @Index(name = "idx_wt_destination_created", columnList = "destination_wallet_id, created_at")
        })
@Getter
@Setter
public class WalletTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "source_wallet_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_source_wallet")
    )
    private Wallet sourceWallet;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "destination_wallet_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_destination_wallet")
    )
    private Wallet destinationWallet;

    // Optional: only set when the client sends an Idempotency-Key header.
    // NULLs do not violate the unique constraint.
    @Column(name = "idempotency_key", updatable = false)
    private String idempotencyKey;


    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 20)
    private TransactionType transactionType;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
