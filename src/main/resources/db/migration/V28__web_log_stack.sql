-- A crash's full stack trace (where in the app it broke), downloadable from the admin's Web Logs.
ALTER TABLE web_logs ADD COLUMN IF NOT EXISTS stack TEXT NOT NULL DEFAULT '';
