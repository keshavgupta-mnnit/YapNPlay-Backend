-- Coins (play money only: never bought, never cashed out). Everyone starts
-- with 1,000; +100 once a day; +500 per fully watched rewarded ad (capped per
-- day); Quick Play costs an entry fee paid into a pot that winners share.
ALTER TABLE users ADD COLUMN coins INT NOT NULL DEFAULT 1000;
-- Days are counted in India time, as days since 1970-01-01.
ALTER TABLE users ADD COLUMN daily_bonus_day INT NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN ad_reward_day INT NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN ad_rewards_today INT NOT NULL DEFAULT 0;

-- Every coin change, for support and abuse checks.
CREATE TABLE coin_ledger (
    id          BIGSERIAL   PRIMARY KEY,
    user_id     UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    delta       INT         NOT NULL,
    reason      VARCHAR(16) NOT NULL,
    match_id    UUID,
    created_at  BIGINT      NOT NULL
);

CREATE INDEX coin_ledger_by_user ON coin_ledger (user_id, created_at DESC);
