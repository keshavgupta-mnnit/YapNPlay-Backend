-- An admin undid a decided report (e.g. a player reported someone by mistake):
-- its block was lifted and the XP it took given back.
ALTER TABLE reports ADD COLUMN reverted_at BIGINT;
