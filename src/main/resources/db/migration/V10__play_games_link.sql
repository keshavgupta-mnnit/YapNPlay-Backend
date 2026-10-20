-- Google Play Games sign-in (contract 0.4.3): one Play Games player ↔ one
-- account. Set the first time a player signs in; used to restore that
-- account on a reinstall or a new phone.
ALTER TABLE users ADD COLUMN play_games_player_id VARCHAR(128) UNIQUE;
