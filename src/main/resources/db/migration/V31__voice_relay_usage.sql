-- Voice relay (TURN) traffic per calendar month (UTC, "YYYY-MM"), as the phones report it:
-- the free relay plans count data a month, and the admin dashboard warns well before the limit.
CREATE TABLE IF NOT EXISTS voice_relay_usage (
    month      VARCHAR(7) PRIMARY KEY,
    bytes      BIGINT     NOT NULL DEFAULT 0,
    updated_at BIGINT     NOT NULL DEFAULT 0
);
