-- Voice relay (TURN) bytes per person per calendar day (dashboard time zone, "YYYY-MM-DD"), for the
-- dashboard's per-day and per-period relay use (people who needed the relay, MB each).
CREATE TABLE IF NOT EXISTS voice_relay_daily (
    day     VARCHAR(10) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    bytes   BIGINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (day, user_id)
);
