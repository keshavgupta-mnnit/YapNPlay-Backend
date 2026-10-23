-- Time each player spends connected to the app (admin dashboard), and when they were last here.
ALTER TABLE users ADD COLUMN online_ms    BIGINT NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN last_seen_at BIGINT NOT NULL DEFAULT 0;
