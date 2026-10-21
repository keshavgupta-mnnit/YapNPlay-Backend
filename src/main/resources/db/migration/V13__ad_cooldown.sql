-- Rewarded ads: when the last one paid (epoch millis), for the gap between ads.
ALTER TABLE users ADD COLUMN last_ad_at BIGINT NOT NULL DEFAULT 0;
