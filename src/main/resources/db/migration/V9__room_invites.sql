-- Group (room) invites from a member to their friends (contract 0.4.1).
-- Accepting one makes the invitee a member at once. An invite goes when it
-- is answered, and with its room or either user.
CREATE TABLE room_invites (
    id          UUID   PRIMARY KEY,
    room_id     UUID   NOT NULL REFERENCES play_rooms (id) ON DELETE CASCADE,
    user_id     UUID   NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    invited_by  UUID   NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at  BIGINT NOT NULL,
    UNIQUE (room_id, user_id)
);

CREATE INDEX room_invites_by_user ON room_invites (user_id, created_at DESC);
