-- Each web build's symbol map (function number → Kotlin name, gzipped), uploaded by its release so
-- the admin's Web Logs show crash stacks with names at once, no backend deploy needed. One a build
-- (its release tag); the newest few are kept.
CREATE TABLE IF NOT EXISTS web_symbol_maps (
    version     VARCHAR(40) PRIMARY KEY,
    uploaded_at BIGINT      NOT NULL,
    data        BYTEA       NOT NULL
);
