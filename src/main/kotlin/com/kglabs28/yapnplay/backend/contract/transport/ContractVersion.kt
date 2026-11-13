package com.kglabs28.yapnplay.backend.contract.transport

/**
 * The contract version. Any change bumps this, is written in
 * docs/backend-api-contract.md, and the golden files change with it.
 *
 * 0.16.0 — voice is peer-to-peer WebRTC (no hosted voice service): VoiceToken.url is "/ws", token is the pass for JoinVoice, new VoiceToken.iceServers (IceServer: urls, username?, credential?; STUN always, TURN when the server has one). VOICE channel: JoinVoice(token) -> VoicePeers(peers); VoicePeers pushed to the voice room on every change; LeaveVoice -> Ack; VoiceSignal(peer, kind offer/answer, sdp) -> Ack, forwarded with peer = sender. The newcomer offers to everyone already there; candidates travel inside the SDP. VoiceRelayUsage(bytes) -> Ack about once a minute while relayed (TURN use for the admin dashboard). Admin kill switches (voice.on, voice.relay_on, voice.relay_cap_gb): Me.voiceOn; with voice off passes and JoinVoice answer VOICE_UNAVAILABLE; a switch turned off pushes VoiceClosed (join again for a pass without the relay).
 * 0.15.0 — Royal Ludo for 4 to 8: RoyalLudoPublicState.arms (4 square, 6 hexagon, 8 octagon board), Colour PINK / TEAL, arm colours in RoyalLudoArmColours; house rule three_pieces; an app that draws the big boards reports the feature "royal-ludo-8" with its games, or it can't be seated at a 5+ player table. Room CPU fill now fills a full table (GameDescriptor.roomTableSize), not just the minimum. GET /matches/history?page= (20 a page, each match once) with MatchHistoryResponse.hasMore. POST /web/log (WebLogBody, plain text) for the web app's load reports. CyberClientTuning keeps only viewCols / viewRows: sizes, the pad, smoothing and sounds are each app's own. Me.launchAd / launchAdGapMinutes (admin settings ads.launch_ad_on, ads.launch_ad_gap_minutes) for the ad when the app opens or comes back to the front. City Royale for 7 and 8 (admin switch cb.eight_players): an app that seats them reports "city-royale-8" with its games; seats 7 and 8 are PINK and TEAL. Royal Ludo's pieces on the 5-8 player boards (3 or 4) come from the admin setting ludo.big_board_pieces, for Quick Play and rooms that don't set three_pieces. Rooms: the admin leads whenever present (else whoever has been online longest); RequestLeader / DenyLeaderRequest and RoomInfo.leaderRequest (someone asking to lead while the admin is away: unrefused in 20 s, they lead and both old and new leader are not ready). Only the leader offers a seat or removes a bot seat.
 * 0.14.2 — RemoveBotSeat (MATCH channel): the room's leader or admin takes a seat the bot plays out of the game (never below two players).
 * 0.14.1 — group size is an admin setting (rooms.max_members, default 16): Me.maxGroupMembers tells the app.
 * 0.14.0 — invite bonus: POST /referrals/claim (ClaimReferralBody -> ReferralClaimed), once per new player (account under 24 h old, the only one on its phone), admin setting coins.referral_bonus (1000) to both; invite links take `?ref=<userCode>`, and /open/... carries the invite to the Play Store (install referrer `open=<kind>/<code>&ref=`) and to the web app (`?open=...&ref=`); GET /.well-known/assetlinks.json.
 * 0.13.4 — Cyber Blast: CyberInput.alt (side way of a diagonal push); CyberBlastPublicState.tuning (CyberClientTuning, admin settings for the app's feel and layout).
 * 0.13.3 — Cyber Blast: CyberInput.tile (a bomb on the tile the player sees, when beside the server's); CyberBlastPublicState.speed / boostSpeed.
 * 0.13.2 — Cyber Blast: CyberPlayerWire.kills / streak; no bomb limit by default, 3 s fuse, reach 1 + kills in a row.
 * 0.13.1 — Cyber Blast room toggles (big_arena, mini_map, fewer_blockers, more_blockers); CyberBlastPublicState.miniMap;
 * tiles are `row * 64 + col` on any board size (cols / rows in the public state).
 * 0.13.0 — Cyber Blast (game id cyber-blast), a real-time game: CyberInput (MATCH channel, acked, nothing
 * forwarded), CyberFrame MatchRelay about every 50 ms, CyberBlastPublicState, CyberHit / CyberOut events.
 * 0.12.5 — web app: POST /auth/google (GoogleSignInBody -> GoogleSignedIn, GOOGLE_SIGN_IN_UNAVAILABLE),
 * POST /auth/ws-ticket (WsTicket) and `/ws?ticket=&games=` (browsers cannot set WebSocket headers), CORS for WEB_APP_ORIGINS.
 * 0.12.4 — SaveRoom(name?) (ROOM channel): the leader saves a Quick Play party as a Game Room with
 * everyone in it (null name: add newcomers); RoomInfo.savedRoomId / savedRoomMissing.
 * 0.12.3 — FriendSummary.activity (OFFLINE, ONLINE, IN_ROOM, PLAYING); PlayInvite push (ROOM channel)
 * when a friend invites the user to a Quick Play party.
 * 0.12.2 — Quick Play with friends: CreateRoom.temporary, RoomInfo.temporary, RoomSummary.temporary,
 * RoomInviteSummary.temporary (a room with one leader, deleted once everyone has left its lobby).
 * 0.12.1 — Doodle King: DoodleKingPublicState.wordCategory / wordLanguage (the word's group and
 * language, shown to the guessers); words are at most 6 letters.
 * 0.12.0 — City Royale: MoneyMoved.reason SELL_BUILDING (a house or hotel sold to the bank);
 * SELL now means a whole city or company sold.
 * 0.11.0 — emoji gifts: SendEmojiGift / EmojiGiftResult on the MATCH channel, EmojiGift
 * (MatchRelay) to the whole table, `GET /coins/emoji-gifts` (EmojiGiftPrices).
 * 0.10.0 — RankedSeat.stake; coinsWon shown for every seat (CPUs and leavers too, display
 * only) and CPUs show completionCoins.
 * 0.9.0 — RenameRoom room message (admin only); InviteToRoomBody.userCodes (invite by
 * player code, friends or not).
 * 0.8.0 — MONO plays on after the first finisher: continueVote (MonoContinueVoteWire) on both
 * MONO public states, VoteContinue action, ContinueVoted event; ranking = finish order, then
 * fewest cards, then (Chaos) knocked-out seats, latest first.
 * 0.7.0 — fixed MONO catch penalties in every mode (MONO 5 / 2, Chaos 7 / 4; the
 * strict_catch house rule is gone); rewarded-ad ladder: AdRewardStatus and CoinsReward
 * gain adRewards, coinsPerAd is the next ad's reward.
 * 0.6.0 — MONO catching: Caught action; MonoCaught, FalseCatch, DeckReshuffled events;
 * catchSeat, catchClosedFor, caughtPenaltyCards, falseCatchPenaltyCards, callBeforePlay,
 * noPowerFinish on both MONO public states; MonoMissed no longer sent. MONO room house rules
 * no_power_finish, call_before_play, strict_catch (stacking_allowed = stack upward).
 * 0.5.0 — coins and leagues (Me coins/levelXp/nextLevelXp/dailyBonus,
 * CoinsReward, Leagues, JoinQueue/QueueStatus/MatchFound entryCoins,
 * NOT_ENOUGH_COINS), keep-playing vote (ExtensionVoteInfo, VoteExtension,
 * ExtensionVoted), room invites, Play Games sign-in, HealthResponse.version.
 * 0.4.0 — matches on the server: MatchSnapshot gains gameId, title,
 * roomId, startedAt, spectators, seatOffer, events; SeatInfo gains
 * displayName, level, micOn, presence; TurnInfo.deadline nullable; Colour
 * PURPLE/ORANGE; GameAction, SetAutoplay, SetMic; Royal Ludo and City
 * Bazaar public state / events / actions; MODERATION channel; join
 * approval (ApproveJoin, DeclineJoin, RoomInfo.waitingList, JoinApproved);
 * error codes BLOCKED, SEAT_TAKEN, VOICE_UNAVAILABLE; JoinRoomResult reply type.
 * 0.3.0 — Quick Play matchmaking, lobby, CPU seats.
 */
const val CONTRACT_VERSION: String = "0.16.0"
