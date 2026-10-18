-- XP comes from match results (placeholder formula in ProgressionPolicy).
ALTER TABLE users ADD COLUMN xp INT NOT NULL DEFAULT 0;

CREATE TABLE match_results (
    id         UUID        PRIMARY KEY,
    game_id    VARCHAR(40) NOT NULL,
    room_id    UUID,
    started_at BIGINT      NOT NULL,
    ended_at   BIGINT      NOT NULL
);

-- One row per seat; user_id is null for CPU seats.
CREATE TABLE match_result_seats (
    match_id   UUID NOT NULL REFERENCES match_results (id) ON DELETE CASCADE,
    seat       INT  NOT NULL,
    user_id    UUID REFERENCES users (id),
    place      INT  NOT NULL,
    xp_awarded INT  NOT NULL DEFAULT 0,
    PRIMARY KEY (match_id, seat)
);

CREATE INDEX match_result_seats_user_idx ON match_result_seats (user_id);
