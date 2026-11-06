-- Google sign-in on the web app (contract 0.12.5): one Google account ↔ one
-- YapNPlay account. Set on the first web sign-in; used to bring that
-- account back in any browser.
ALTER TABLE users ADD COLUMN google_subject VARCHAR(255) UNIQUE;
