-- The phone a guest account was made on (Android's per-app id), so the same
-- phone gets its guest back after a reinstall instead of a new one.
ALTER TABLE users ADD COLUMN device_id VARCHAR(64);
CREATE INDEX users_device_idx ON users (device_id);
