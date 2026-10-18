-- Rooms and who belongs to them. Presence, ready flags and the countdown
-- live in memory and are rebuilt after a restart.
CREATE TABLE play_rooms (
    id            UUID        PRIMARY KEY,
    code          CHAR(6)     NOT NULL UNIQUE,
    name          VARCHAR(30) NOT NULL,
    description   VARCHAR(80),
    icon          VARCHAR(16) NOT NULL,
    created_by    UUID        NOT NULL REFERENCES users (id),
    leader_id     UUID        NOT NULL REFERENCES users (id),
    admin_id      UUID        NOT NULL REFERENCES users (id),
    selected_game VARCHAR(40),
    bot_fill      BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at    BIGINT      NOT NULL
);

CREATE TABLE play_room_members (
    room_id   UUID   NOT NULL REFERENCES play_rooms (id) ON DELETE CASCADE,
    user_id   UUID   NOT NULL REFERENCES users (id),
    joined_at BIGINT NOT NULL,
    PRIMARY KEY (room_id, user_id)
);

CREATE INDEX play_room_members_user_idx ON play_room_members (user_id);
