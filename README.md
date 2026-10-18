# YapNPlay backend

The game server for the YapNPlay Android app (`..\..\AP\YapNPlay`): guest
accounts, rooms, the real-time WebSocket, and later matches, matchmaking
and voice tokens. Kotlin + Ktor, PostgreSQL.

The wire contract is `AP\YapNPlay\docs\backend-api-contract.md`. App and
server share **no code**: only JSON crosses the network. This repo has its
own message classes (`contract/`) and owns the game rules (`games/`); the
app only draws what the server sends.

**Golden files** keep the two sides in step: `src/test/resources/golden/`
holds the exact JSON of every message, and `GoldenContractTest` fails if
the server writes anything else. Changing a message on purpose: run
`.\gradlew.bat test "-Dgolden.update=true"`, review the changed files,
bump `CONTRACT_VERSION`, update the contract doc, and give the app the same
files.

## Words used here

- **Server**: a program that waits for requests and answers them.
- **Port**: a numbered door on a computer; this server listens on 8080.
- **HTTP**: one request, one answer (`GET /me`).
- **WebSocket**: a connection that stays open so the server can push
  updates (room snapshots) without being asked.
- **Database / PostgreSQL**: where users and rooms are stored for good.
- **Migration**: a numbered SQL file (`src/main/resources/db/migration`)
  that changes the database; each runs exactly once, in order.
- **Actor**: one worker per room with an inbox; it handles requests one at
  a time, so two people can never break a room by acting at once.

## Run it on the laptop (for checking only)

The real server will run in the cloud. Locally you only run it to try
something. No Docker needed: in DEV_MODE the server starts its own
PostgreSQL and keeps the data in `data/postgres` (git-ignored).

```powershell
$env:DEV_MODE = "true"; .\gradlew.bat run
```

Expect `Responding at http://127.0.0.1:8080` after about 30 seconds the first
time. Check it in a browser: http://localhost:8080/health. Stop with Ctrl+C.

## Run the tests

```powershell
.\gradlew.bat test
```

186 tests: golden JSON for every message, both game engines and full CPU matches, matchmaking, moderation, voice passes, profiles and friends, rules
(names, codes, leader/admin hand-over, who gets a seat), the room actor on
virtual time, and the HTTP + WebSocket API against a real PostgreSQL
started for the test run. Report: `build\reports\tests\test\index.html`.

## Settings (environment variables)

| Variable | Default | Meaning |
|---|---|---|
| `SERVER_PORT` / `PORT` | 8080 | Port to listen on (hosts like Render set `PORT`). |
| `DEV_MODE` | false | Allows the embedded database and `?token=` on `/ws`. Never on in the cloud. |
| `DB_URL` | none | JDBC URL, e.g. `jdbc:postgresql://host/db?sslmode=require`. Required unless DEV_MODE. |
| `DB_USER`, `DB_PASSWORD` | empty | Database login. **Secrets: set them in the host's dashboard, never in code or git.** |
| `DB_POOL_SIZE` | 5 | Database connections kept open. |
| `MAX_ROOMS_PER_USER` | 5 | Rooms one user may belong to. |
| `COUNTDOWN_SECONDS` | 10 | Room start countdown. |
| `GUEST_CREATIONS_PER_HOUR` | 20 | New guests per IP address per hour. |
| `DISABLED_GAMES` | empty | Comma-separated game ids switched off (`royal-ludo,city-bazaar`). |
| `EMBEDDED_DB_DIR`, `EMBEDDED_DB_PORT` | `data/postgres`, 54329 | DEV_MODE database only. |

See `.env.example`. A real `.env` is git-ignored.

## HTTP

All need `Authorization: Bearer <userId>.<secret>` except `/health` and `/auth/guest`.
Errors are `{"code": "<ErrorCode>", "message": "…"}`.

| Call | Answer |
|---|---|
| `GET /health` | `{status, contractVersion}` |
| `POST /auth/guest` | 201 `GuestCreated(userId, secret)` (secret shown once; rate-limited) |
| `GET /me` | `Me` (profile, 6-character `userCode`, XP/level, games played/won) |
| `PATCH /me` `UpdateProfileBody` | `Me` (only the fields sent change) |
| `GET /rooms/mine` | `MyRooms` with member and live counts |
| `GET /rooms/preview/{code}` | `RoomPreview` (`INVALID_CODE`, `NOT_FOUND`) |
| `GET /stats/online` | `OnlineStats` |
| `GET /friends` | `Friends` (mutual friends, online first) |
| `GET /friends/requests` | `FriendRequests` (sent to the user, newest first) |
| `POST /friends/requests` `SendFriendRequestBody(userCode)` | `SendFriendRequestReply(outcome)` |
| `POST /friends/requests/{id}/accept` · `/decline` | 204 |
| `DELETE /friends/{userId}` | 204 (both ways) |
| `POST /blocks` `BlockUserBody` · `GET /blocks` · `DELETE /blocks/{userId}` | 204 · `BlockedUsers` · 204 |
| `POST /devices` `RegisterDevice` | 204 (Firebase push token) |
| `POST /rooms/{roomId}/voice-token` · `/matches/{matchId}/voice-token` | `VoiceToken` (10 min; `VOICE_UNAVAILABLE` without LiveKit) |
| `POST /penalties/clear-with-ad` | 204 |

Matches, Quick Play and moderation run on the WebSocket; see
`CONTRACT-0.4.0.md` for every message added since 0.3.1.

## WebSocket `/ws`

One connection per user; a new one closes the old with code 4000
`SessionReplaced`. The server pings every 15 s and drops a connection
silent for 45 s. Messages are `Envelope(requestId, channel, type, payload)`,
at most 8 KB, at most 20 per second.

**Every request gets exactly one reply with the same `requestId`:** `Ack`,
`Rejected(requestId, code, message)`, or a typed result. A repeated
`requestId` gets the original reply without running again. Pushes (room
snapshots, `MatchStarting`) carry a server-made `requestId`.

ROOM channel (the room is always the one you are present in; no message
carries a user id list):

| Send | Reply | Notes |
|---|---|---|
| `CreateRoom(name?)` | `RoomSnapshot` | You become member, leader and admin. |
| `JoinRoom(code)` | `JoinRoomResult` | Joins or re-enters; moves you out of another room. `ROOM_FULL` (10), `ROOM_LIMIT_REACHED`, `ALREADY_IN_ROOM` while seated in another room's countdown. |
| `RequestSnapshot(lastVersion)` | `RoomSnapshot` | |
| `ExitLobby` / `LeaveRoom` | `Ack` | Offline but still a member / give up membership. |
| `SelectGame(gameId?)` | `Ack` | Leader. Resets ready/watching, loads default house rules. |
| `SetHouseRule(key, on)`, `SetBotFill(on)` | `Ack` | Leader. |
| `SetReady`, `SetWatching`, `SetVoiceState` | `Ack` | Yourself. Ready needs a game. |
| `StartCountdown` / `CancelCountdown` | `Ack` | Leader starts (≥ 2 ready humans incl. leader, and the game minimum or bot fill); any ready player cancels. |
| `TransferLeader(userId)` / `TransferAdmin(userId)` / `RemoveMember(userId)` | `Ack` | Leader / admin / admin. |

After every change all present members get `RoomSnapshot(version, serverTime, room)`.
At zero the server checks again, seats the first `maxPlayers` ready humans
(leader first, then by ready time), adds bots only up to `minPlayers`, and
pushes `MatchStarting(matchId)`. MATCH and MATCHMAKING answer `Rejected`
until Phase 10.

## Code layout

```
src/main/kotlin/com/kglabs28/yapnplay/backend/
  Application.kt     start-up and wiring
  config/            settings, constants, texts
  contract/          wire messages (JSON shapes from the contract)
  games/             game rules: base contracts, Royal Ludo, City Bazaar
  domain/            pure rules and interfaces (identity, rooms)
  application/       use cases, room actors, envelope codec
  infrastructure/    PostgreSQL (Exposed + Flyway), hashing, clock, ids
  api/               HTTP routes, sign-in, WebSocket
  di/                Koin modules
  plugins/           Ktor plugins (JSON, logging, errors, auth, sockets, routes)
```

## Known gaps (next phases)

- Join approval (admin's Waiting List) needs new protocol messages; for now
  the code alone lets you join.
- `JoinRoomResult` (reply to `JoinRoom`) is in this repo's `MessageType`;
  the app's `MessageType` should add it when Phase 12 wires the lobby.
- Admin succession uses seniority until a room leaderboard exists.
- `isFriend` is always false (friends are post-POC); `POST /devices`,
  voice tokens, matches and matchmaking come in later phases.
- Room actors stay in memory until the room is deleted.
