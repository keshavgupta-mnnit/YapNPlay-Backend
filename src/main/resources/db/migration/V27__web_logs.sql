-- What web browsers report while the web app loads and runs (failures, slow starts, a start
-- with its time and device, crashes), so problems on phones we can't reach show up in the
-- admin dashboard. No account or address: only the browser's own description of itself.
CREATE TABLE IF NOT EXISTS web_logs (
    id          UUID PRIMARY KEY,
    created_at  BIGINT       NOT NULL,
    kind        VARCHAR(20)  NOT NULL,
    stage       VARCHAR(80)  NOT NULL,
    message     VARCHAR(500) NOT NULL,
    user_agent  VARCHAR(300) NOT NULL,
    device      VARCHAR(200) NOT NULL,
    elapsed_ms  BIGINT       NOT NULL,
    version     VARCHAR(40)  NOT NULL
);
CREATE INDEX IF NOT EXISTS web_logs_created_idx ON web_logs (created_at);
