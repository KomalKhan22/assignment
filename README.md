# Wallet & Transfer Service

Spring Boot 4 / Java 17 REST API for customer wallets and money transfers.

## Run

```bash
# Default: in-memory H2 (zero setup)
./mvnw spring-boot:run

# MySQL via Docker Compose (db/mysql/01_schema.sql is applied automatically on first start)
cp .env.example .env            # then edit the passwords; .env is git-ignored
docker compose up -d
export DB_PASSWORD=<same value as in .env>
./mvnw spring-boot:run -Dspring-boot.run.profiles=mysql

# Tests (unit + integration, H2)
./mvnw test
```

## Database scripts (MySQL)

| File | Purpose |
|---|---|
| `db/mysql/01_schema.sql` | Creates `wallet` and `wallet_transaction` (PKs, FKs, unique idempotency key, history indexes, CHECK constraints) |
| `db/mysql/99_drop_schema.sql` | Dev helper to drop the tables; never mounted automatically |

Manual run: `mysql -u wallet -p wallet < db/mysql/01_schema.sql`. If you already started the container once,
the init script will not run again - recreate the volume with `docker compose down -v` first.

## API

| Method | Path | Notes |
|---|---|---|
| POST | `/api/wallets` | `{"customerId","currency"}` -> 201 + wallet (balance 0) |
| GET | `/api/wallets/{walletId}` | 200 / 404 |
| POST | `/api/wallets/{walletId}/deposit` | helper (not in the spec) so wallets can be funded |
| POST | `/api/transfers` | `{"sourceWalletId","destinationWalletId","amount"}`; optional `Idempotency-Key` header |
| GET | `/api/wallets/{walletId}/transactions?page=0&size=20` | newest first, paginated, incoming + outgoing |

Errors share one shape (`timestamp, status, error, message, fieldErrors`):
400 validation / same wallet / currency mismatch, 404 unknown wallet, 409 idempotency conflict,
422 insufficient funds.


