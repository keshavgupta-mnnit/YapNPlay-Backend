-- Profile (contract 0.3.1): @username (not unique), a unique 6-character
-- ID friends type to add each other, and a preset avatar.
ALTER TABLE users ADD COLUMN username  VARCHAR(20) NOT NULL DEFAULT '';
ALTER TABLE users ADD COLUMN user_code CHAR(6) UNIQUE;
ALTER TABLE users ADD COLUMN avatar_id VARCHAR(8)  NOT NULL DEFAULT '';

-- Friendship is mutual now: one row each way, written together.
CREATE TABLE friend_requests (
    id         UUID   PRIMARY KEY,
    from_id    UUID   NOT NULL REFERENCES users (id),
    to_id      UUID   NOT NULL REFERENCES users (id),
    created_at BIGINT NOT NULL,
    UNIQUE (from_id, to_id)
);

CREATE TABLE blocks (
    blocker_id UUID   NOT NULL REFERENCES users (id),
    blocked_id UUID   NOT NULL REFERENCES users (id),
    created_at BIGINT NOT NULL,
    PRIMARY KEY (blocker_id, blocked_id)
);
