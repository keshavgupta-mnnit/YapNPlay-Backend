-- A seat whose player left before the match ended: their history shows it as abandoned.
ALTER TABLE match_result_seats ADD COLUMN left_match BOOLEAN NOT NULL DEFAULT FALSE;
