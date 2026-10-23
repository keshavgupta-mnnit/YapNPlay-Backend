-- Feedback and support: a player's message (a new game idea, a feature, an
-- issue, a bug, feedback) and the conversation the admins reply in.
CREATE TABLE feedback_tickets (
    id         UUID        PRIMARY KEY,
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    kind       VARCHAR(30) NOT NULL,
    status     VARCHAR(20) NOT NULL,
    created_at BIGINT      NOT NULL,
    updated_at BIGINT      NOT NULL
);
CREATE INDEX feedback_tickets_user_idx ON feedback_tickets (user_id, updated_at DESC);
CREATE INDEX feedback_tickets_updated_idx ON feedback_tickets (updated_at DESC);

CREATE TABLE feedback_messages (
    id         UUID        PRIMARY KEY,
    ticket_id  UUID        NOT NULL REFERENCES feedback_tickets (id) ON DELETE CASCADE,
    from_admin BOOLEAN     NOT NULL,
    author     VARCHAR(64) NOT NULL DEFAULT '',
    body       TEXT        NOT NULL,
    created_at BIGINT      NOT NULL
);
CREATE INDEX feedback_messages_ticket_idx ON feedback_messages (ticket_id, created_at);
