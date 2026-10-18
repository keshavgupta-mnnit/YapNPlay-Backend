-- Voice-chat reports decided by the people present.
CREATE TABLE reports (
    id          UUID        PRIMARY KEY,
    context_id  VARCHAR(64) NOT NULL,
    reporter_id UUID        NOT NULL REFERENCES users (id),
    reported_id UUID        NOT NULL REFERENCES users (id),
    reason      VARCHAR(32) NOT NULL,
    required    INT         NOT NULL,
    created_at  BIGINT      NOT NULL,
    resolved_at BIGINT,
    upheld      BOOLEAN
);

-- Every person asked gets a ballot: AGREE, DISAGREE or NO_RESPONSE. Never sent to clients.
CREATE TABLE report_ballots (
    report_id   UUID        NOT NULL REFERENCES reports (id) ON DELETE CASCADE,
    voter_id    UUID        NOT NULL REFERENCES users (id),
    response    VARCHAR(16) NOT NULL,
    recorded_at BIGINT      NOT NULL,
    PRIMARY KEY (report_id, voter_id)
);

-- Incidents: points taken and new matches refused until blocked_until.
CREATE TABLE penalties (
    id            UUID   PRIMARY KEY,
    user_id       UUID   NOT NULL REFERENCES users (id),
    report_id     UUID   REFERENCES reports (id),
    points        INT    NOT NULL,
    blocked_until BIGINT NOT NULL,
    created_at    BIGINT NOT NULL,
    cleared_at    BIGINT
);

CREATE INDEX penalties_user_idx ON penalties (user_id);
