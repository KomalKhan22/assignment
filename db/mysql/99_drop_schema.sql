-- Development helper: removes all tables created by 01_schema.sql.
-- NOT mounted into the container; run it manually when you want a clean slate.
DROP TABLE IF EXISTS wallet_transaction;
DROP TABLE IF EXISTS wallet;
