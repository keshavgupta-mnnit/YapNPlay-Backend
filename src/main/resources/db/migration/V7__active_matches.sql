-- Unfinished matches, saved after every change so a restart or deploy can
-- bring them back. The row goes when the match ends. [state] is the
-- server's own JSON checkpoint (MatchCheckpoint), not a contract shape.
CREATE TABLE active_matches (
    id         VARCHAR(64) PRIMARY KEY,
    game_id    VARCHAR(40) NOT NULL,
    room_id    VARCHAR(64),
    version    BIGINT      NOT NULL,
    updated_at BIGINT      NOT NULL,
    state      TEXT        NOT NULL
);
