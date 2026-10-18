-- Friends are one-way: "I added you". Provisional until friends are designed.
CREATE TABLE friendships (
    user_id    UUID   NOT NULL REFERENCES users (id),
    friend_id  UUID   NOT NULL REFERENCES users (id),
    created_at BIGINT NOT NULL,
    PRIMARY KEY (user_id, friend_id)
);

-- Firebase push tokens, one row per user and device token.
CREATE TABLE devices (
    user_id    UUID         NOT NULL REFERENCES users (id),
    token      VARCHAR(512) NOT NULL,
    platform   VARCHAR(16)  NOT NULL,
    updated_at BIGINT       NOT NULL,
    PRIMARY KEY (user_id, token)
);

-- The admin's Waiting List.
CREATE TABLE join_requests (
    room_id      UUID   NOT NULL REFERENCES play_rooms (id) ON DELETE CASCADE,
    user_id      UUID   NOT NULL REFERENCES users (id),
    requested_at BIGINT NOT NULL,
    PRIMARY KEY (room_id, user_id)
);
