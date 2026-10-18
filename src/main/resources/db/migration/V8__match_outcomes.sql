-- Every match's outcome, so unfinished games can be counted. A row is
-- written when the match starts (RUNNING) and closed when it ends:
-- COMPLETED (played to the end), ABORTED_SERVER (a restart or crash it
-- could not come back from) or ABORTED_ERROR (the match broke). room_id
-- null = Quick Play. error_count = single actions that failed unexpectedly.
CREATE TABLE match_outcomes (
    match_id    VARCHAR(64) PRIMARY KEY,
    game_id     VARCHAR(40) NOT NULL,
    room_id     VARCHAR(64),
    started_at  BIGINT      NOT NULL,
    ended_at    BIGINT,
    status      VARCHAR(20) NOT NULL,
    reason      TEXT,
    error_count INT         NOT NULL DEFAULT 0
);

CREATE INDEX match_outcomes_started_idx ON match_outcomes (started_at);
CREATE INDEX match_outcomes_status_idx ON match_outcomes (status);
