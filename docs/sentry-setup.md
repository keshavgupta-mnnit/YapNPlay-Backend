# Server error tracking with Sentry (free)

The backend sends its errors to Sentry, but only once you give it your
project's key (the **DSN**). Until then nothing changes: errors stay in the
server log. Sentry's free **Developer** plan (about 5,000 errors a month,
one user) is enough until the app is live, and needs no card.

What reaches Sentry: everything the server logs as an error (crashed HTTP
calls, failed match or room actions, background work that crashed), with
the last log lines before it. Sign-in tokens, cookies and secrets are
removed before anything is sent; players appear only by their user id.

## 1. Create the account and project (once)

1. Open <https://sentry.io/signup/> and sign up (Google sign-in is fine).
   Pick the free **Developer** plan if asked; skip any trial upgrade.
2. When it asks you to create a project, choose the platform **Java**
   (plain Java, not Spring or Android), keep the alert setting "Alert me on
   every new issue", and name it `yapnplay-backend`.
3. It then shows setup code. Ignore the code; you only need the **DSN**,
   a URL that looks like `https://<letters>@o<number>.ingest.sentry.io/<number>`.
   You can find it again at **Settings → Projects → yapnplay-backend →
   Client Keys (DSN)**.

## 2. Give it to the backend (never to chat or GitHub)

1. Open `.env` in this folder (the backend's, `BP\YapNPlay\.env`).
2. Add one line with your DSN (no quotes):

   ```
   SENTRY_DSN=https://...your DSN...
   ```

3. Save, then restart the server. The `.env` file is read only at start.
   On start you should see: `Server errors are reported to Sentry`.

`.env` is git-ignored, so the DSN never goes to GitHub. When the server is
hosted, the same `SENTRY_DSN` goes into the host's secret settings (GitHub
Secrets for the deploy), never into the code.

## 3. Check it works

Send a test error with your admin token (PowerShell):

```powershell
Invoke-RestMethod -Method Post -Uri http://localhost:8080/admin/test-error -Headers @{ Authorization = "Bearer <your ADMIN_TOKEN>" }
```

- `sentryEnabled : True` → open sentry.io → **Issues**. Within a minute you
  see "Test error from the YapNPlay admin: Sentry is working", and Sentry
  emails you about the new issue.
- `sentryEnabled : False` → the server did not get the DSN. Check the
  `.env` line and restart.

## Reading it

- **Issues**: one row per kind of error, with how often it happened and
  the last time. Open one for the full stack trace and the log lines before it.
- **Environment** filter: `local` is your laptop (DEV_MODE), `production`
  the hosted server.
- **Release** shows the backend's contract version, so you can tell which
  version had the error.
- Resolve an issue once it is fixed; Sentry reopens it if it comes back.
