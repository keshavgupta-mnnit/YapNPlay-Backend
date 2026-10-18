# Contract 0.4.0 — what the server added on top of 0.3.1

To be merged into `AP\YapNPlay\docs\backend-api-contract.md` once nobody
else is editing that file. The exact JSON of every message is in
`src/test/resources/golden/` (one file per message); copy those files to
the app's tests so both sides check the same shapes.

## Error codes
`BLOCKED` (a penalty refuses new matches), `SEAT_TAKEN` (someone took the
open seat first), `VOICE_UNAVAILABLE` (voice not set up on the server).

## Channels
New `MODERATION` channel (ReportPlayer, CastReportVote in; ReportVoteRequested,
ReportResolved, PenaltyIssued out).

## Reply rule (all channels)
Every request gets exactly one reply with the same `requestId`: `Ack`,
`Rejected`, or a typed result — `JoinRoomResult` for `JoinRoom`,
`RoomSnapshot` for `CreateRoom` / `RequestSnapshot`, `MatchSnapshot` for
`MatchRequestSnapshot`. Pushes carry a server-made `requestId`.

## ROOM channel
- `JoinRoom(code)` from a non-member now answers `PENDING_APPROVAL` and puts
  them on the admin's Waiting List (decisions.md "invites").
- `ApproveJoin(userId)`, `DeclineJoin(userId)` — admin only (`NOT_ADMIN`).
- `RoomInfo.waitingList: [JoinRequestInfo(userId, displayName, requestedAt)]`
  — filled only in the admin's snapshot.
- `JoinApproved(roomId, roomName)` pushed to the requester (plus an FCM
  `room_activity` push).
- `RoomInfo.matchInProgress` is set while the room's match runs.
- `RoomMember.isFriend` is real (mutual friends, relative to the receiver).

## MATCH channel
Client → server (each carries `matchId`):
`MatchRequestSnapshot(matchId, lastVersion)` (a present room member without
a seat starts watching), `RollDice`, `MovePiece(pieceId)` (Royal Ludo),
`GameAction(matchId, action: {type, ...})` (City Bazaar: RollDice, BuyTile,
DeclineTile, BuildOnTile(tile), SkipBuild, SellBuilding(tile), SellTile(tile),
StartAuction(tile), PlaceBid, PayJailFine, StayInJail, DeclareBankruptcy),
`SetAutoplay(enabled)`, `SetMic(micOn)`, `SetPresence(away)`,
`ConfirmPresence`, `LeaveSeat(giveToUserId?)`, `AnswerSeatOffer(accept)`,
`ChooseSeatTaker(giveToUserId?)`, `TakeSeat(seat)`.

Server → client: `MatchSnapshot` per viewer after every change, `MatchEnded`.

`MatchSnapshot` gained: `gameId`, `title`, `roomId?`, `startedAt`,
`spectators: [SpectatorInfo(userId, displayName, micOn)]`,
`seatOffer?: SeatOfferInfo(seat, leaverName, stage, endsAt, userId?, name?, round?)`
with `stage` ∈ CONFIRMING_ABSENCE / TO_SPECTATOR / LEADER_CHOOSING / OPEN_TO_ALL,
and `events: [ {type, ...} ]` — what happened since the viewer's last
snapshot, in order, for animation. `TurnInfo.deadline` is null while play
is on hold or finished. `SeatInfo` gained `displayName`, `level`, `micOn`,
`presence` (PLAYING / AWAY / BOT). `Colour` gained PURPLE, ORANGE.

`publicState`:
- Royal Ludo: `{pieces: {seat: [progress x4]}, phase: ROLL|MOVE|FINISHED,
  lastRoll?, legalPieces (viewer only), finishedSeats, ranking}`.
  Events: Rolled(seat, value), Moved(seat, piece, from, to),
  Captured(seat, victimSeat, victimPiece), TurnPassed(seat), Reverted(seat, pieces).
- City Bazaar: `{players: [{seat, cash, position, inJail, halfRent, bankrupt}],
  owners, levels, phase, die1, die2, pendingTile?, debt?{amount, reason, tile?},
  jailTriesLeft, auction?{tile, sellerSeat, bid, leaderSeat?, nextBid, endsAt},
  timeEndsAt?, round, roundLimit?, netWorth, ranking}`.
  Events: Rolled, Moved, Money(from?, to?, amount, reason, tile?), Card,
  Bought, CantAfford, Built, Sold, Jailed, Freed, HalfRent, AuctionStarted,
  BidPlaced, AuctionEnded, Bankrupt, Skipped, TurnPassed.

## HTTP
- `POST /matches/{matchId}/voice-token` → `VoiceToken` (players and watchers;
  a room match uses the room's voice room).
- `POST /rooms/{roomId}/voice-token` → `VoiceToken` (members), 10-minute pass.
- `POST /devices`, `POST /penalties/clear-with-ad` → 204.
- Profile, friends and blocks exactly as the app contract (0.3.1 + `GET /blocks`, `DELETE /blocks/{userId}`); `avatarId` accepts `a01`…`a18`.

## Rules the server applies
- XP placeholder: 100 for 1st, 20 less per place (at least 10); a level
  every 500 XP; penalties take 50 points. `gamesPlayed`/`gamesWon` counted
  from stored results.
- Reports: 15 s vote, 2 agreements with ≤4 voters else 3, loser blocked 10 min.
