-- Web crash reporting like Crashlytics: each event knows its browser (install_id: users affected)
-- and page load (page_load: sessions), how often it happened in that load (occurrences), the page's
-- state and the app's keys (context, JSON) and the last things that happened (breadcrumbs, a line
-- each). Issues group events by their error; web_issues keeps an issue closed (closed_at > 0) until
-- it happens again after that (regressed).
ALTER TABLE web_logs ADD COLUMN IF NOT EXISTS install_id  VARCHAR(40) NOT NULL DEFAULT '';
ALTER TABLE web_logs ADD COLUMN IF NOT EXISTS page_load   VARCHAR(40) NOT NULL DEFAULT '';
ALTER TABLE web_logs ADD COLUMN IF NOT EXISTS occurrences INT         NOT NULL DEFAULT 1;
ALTER TABLE web_logs ADD COLUMN IF NOT EXISTS context     TEXT        NOT NULL DEFAULT '';
ALTER TABLE web_logs ADD COLUMN IF NOT EXISTS breadcrumbs TEXT        NOT NULL DEFAULT '';

CREATE TABLE IF NOT EXISTS web_issues (
    fingerprint VARCHAR(64)  PRIMARY KEY,
    closed_at   BIGINT       NOT NULL DEFAULT 0,
    closed_by   VARCHAR(80)  NOT NULL DEFAULT '',
    note        VARCHAR(500) NOT NULL DEFAULT ''
);
