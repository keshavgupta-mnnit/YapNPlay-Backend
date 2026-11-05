-- Quick Play with friends: a temporary room, gone once everyone has left its lobby.
-- It never shows in Your Rooms and never counts toward the rooms-per-user limit.
ALTER TABLE play_rooms ADD COLUMN temporary BOOLEAN NOT NULL DEFAULT FALSE;
