-- Where each account was made, for the admin dashboard: the Android app or the
-- web app, and for the web the device's system (ios, android, windows, mac,
-- linux, other). New accounts record it at sign-in; older ones are filled in
-- from what they already have (a phone id or Play Games: the app; a Google
-- web sign-in: the web), and their device system stays unknown.
ALTER TABLE users ADD COLUMN platform  VARCHAR(10);
ALTER TABLE users ADD COLUMN device_os VARCHAR(10);

UPDATE users SET platform = 'android' WHERE device_id IS NOT NULL OR play_games_player_id IS NOT NULL;
UPDATE users SET platform = 'web' WHERE platform IS NULL AND google_subject IS NOT NULL;

CREATE INDEX IF NOT EXISTS users_created_idx ON users (created_at);
