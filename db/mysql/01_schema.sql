-- =====================================================================
-- Wallet & Transfer Service - MySQL 8 schema
--
-- Run automatically by Docker Compose on the first start of the MySQL
-- container (mounted into /docker-entrypoint-initdb.d), or manually:
--
--     mysql -u <user> -p <database> < db/mysql/01_schema.sql
--
-- Notes
--  * UUID primary keys are stored as BINARY(16) - this is exactly how
--    Hibernate 6 maps java.util.UUID on MySQL, so no converters are needed.
--  * Money is DECIMAL(19,2), matching the entity mapping.
--  * Timestamps are DATETIME(6) in UTC (java.time.Instant).
-- =====================================================================

CREATE TABLE IF NOT EXISTS wallet (
    id          BINARY(16)     NOT NULL,
    customer_id VARCHAR(100)   NOT NULL,
    currency    CHAR(3)        NOT NULL,
    balance     DECIMAL(19, 2) NOT NULL DEFAULT 0.00,
    created_at  DATETIME(6)    NOT NULL,
    updated_at  DATETIME(6)    NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT chk_wallet_balance_non_negative CHECK (balance >= 0)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS wallet_transaction (
    id                    BINARY(16)     NOT NULL,
    source_wallet_id      BINARY(16)     NOT NULL,
    destination_wallet_id BINARY(16)     NOT NULL,
    idempotency_key       VARCHAR(255)   NULL,
    amount                DECIMAL(19, 2) NOT NULL,
    currency              CHAR(3)        NOT NULL,
    transaction_type      VARCHAR(20)    NOT NULL,
    created_at            DATETIME(6)    NOT NULL,
    PRIMARY KEY (id),
    -- NULLs are allowed multiple times: only requests that send an Idempotency-Key are constrained.
    CONSTRAINT wallet_transaction_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT fk_source_wallet      FOREIGN KEY (source_wallet_id)      REFERENCES wallet (id),
    CONSTRAINT fk_destination_wallet FOREIGN KEY (destination_wallet_id) REFERENCES wallet (id),
    CONSTRAINT chk_wt_amount_positive CHECK (amount > 0),
    CONSTRAINT chk_wt_type CHECK (transaction_type IN ('DEPOSIT', 'TRANSFER'))
) ENGINE = InnoDB;

-- Transaction history is queried per wallet, newest first.
CREATE INDEX idx_wt_source_created      ON wallet_transaction (source_wallet_id, created_at);
CREATE INDEX idx_wt_destination_created ON wallet_transaction (destination_wallet_id, created_at);
