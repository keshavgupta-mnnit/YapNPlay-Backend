-- One row per stretch someone spent in a voice room (written when it ends), for the admin
-- dashboard's voice usage: people who used voice, sessions, minutes, per day.
CREATE TABLE IF NOT EXISTS voice_sessions (
    id         BIGSERIAL   PRIMARY KEY,
    user_id    VARCHAR(64) NOT NULL,
    started_at BIGINT      NOT NULL,
    ended_at   BIGINT      NOT NULL,
    seconds    INT         NOT NULL
);
CREATE INDEX IF NOT EXISTS voice_sessions_started_at ON voice_sessions (started_at);
