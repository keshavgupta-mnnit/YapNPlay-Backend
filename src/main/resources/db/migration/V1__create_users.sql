-- Guest accounts. Only the SHA-256 of the secret is stored (64 hex chars).
CREATE TABLE users (
    id           UUID        PRIMARY KEY,
    display_name VARCHAR(24) NOT NULL,
    secret_hash  CHAR(64)    NOT NULL,
    created_at   BIGINT      NOT NULL -- epoch millis
);
