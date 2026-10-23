-- Match history: who sat where (name and avatar as they were), and the coins each seat
-- won, got for finishing and staked, so a player can look back at their matches.
ALTER TABLE match_result_seats ADD COLUMN display_name     VARCHAR(64) NOT NULL DEFAULT '';
ALTER TABLE match_result_seats ADD COLUMN avatar_id        VARCHAR(40) NOT NULL DEFAULT '';
ALTER TABLE match_result_seats ADD COLUMN coins_won        INT         NOT NULL DEFAULT 0;
ALTER TABLE match_result_seats ADD COLUMN completion_coins INT         NOT NULL DEFAULT 0;
ALTER TABLE match_result_seats ADD COLUMN stake            INT         NOT NULL DEFAULT 0;

CREATE INDEX match_results_ended_idx ON match_results (ended_at DESC);
