-- The league a Game Room plays in: every player's entry (coins) for its matches. 100 by default.
ALTER TABLE play_rooms ADD COLUMN entry_coins INTEGER NOT NULL DEFAULT 100;
