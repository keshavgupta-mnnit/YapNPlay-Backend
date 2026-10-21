-- Rewarded ads: the app sends an id per ad view; the same id never pays twice.
ALTER TABLE coin_ledger ADD COLUMN ref VARCHAR(64);
CREATE UNIQUE INDEX coin_ledger_ref ON coin_ledger (user_id, ref) WHERE ref IS NOT NULL;

-- Completion coins: how many were paid today (India time), for the daily cap.
ALTER TABLE users ADD COLUMN completion_day INT NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN completions_today INT NOT NULL DEFAULT 0;

-- Game settings changed from the admin dashboard without a deploy.
-- value is JSON (a number or a list of numbers); a missing key uses the code's default.
CREATE TABLE game_settings (
    key         VARCHAR(64)  PRIMARY KEY,
    value       TEXT         NOT NULL,
    updated_at  BIGINT       NOT NULL,
    updated_by  VARCHAR(64)  NOT NULL
);

-- Who changed what in the admin dashboard (settings, blocks, reports, coins).
CREATE TABLE admin_audit (
    id          BIGSERIAL    PRIMARY KEY,
    action      VARCHAR(32)  NOT NULL,
    target      VARCHAR(64)  NOT NULL,
    old_value   TEXT,
    new_value   TEXT,
    changed_by  VARCHAR(64)  NOT NULL,
    note        VARCHAR(200),
    changed_at  BIGINT       NOT NULL
);

CREATE INDEX admin_audit_by_time ON admin_audit (changed_at DESC);
