-- Admin dashboard sign-in: one row per admin (ID + salted PBKDF2 password
-- hash + permissions) and their sign-in sessions (only a hash of each token).
-- The first account (admin / admin) is created by the server on the first
-- sign-in attempt while the table is empty.
CREATE TABLE admin_accounts (
    admin_id            VARCHAR(32)  PRIMARY KEY,
    password_hash       VARCHAR(128) NOT NULL,
    salt                VARCHAR(64)  NOT NULL,
    read_only           BOOLEAN      NOT NULL DEFAULT FALSE,
    can_edit_settings   BOOLEAN      NOT NULL DEFAULT FALSE,
    can_remove_players  BOOLEAN      NOT NULL DEFAULT FALSE,
    can_create_admins   BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at          BIGINT       NOT NULL,
    created_by          VARCHAR(32)  NOT NULL
);

CREATE TABLE admin_sessions (
    token_hash  CHAR(64)     PRIMARY KEY,
    admin_id    VARCHAR(32)  NOT NULL REFERENCES admin_accounts (admin_id) ON DELETE CASCADE,
    expires_at  BIGINT       NOT NULL
);

CREATE INDEX admin_sessions_by_admin ON admin_sessions (admin_id);
