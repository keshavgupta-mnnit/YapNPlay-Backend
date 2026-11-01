package com.kglabs28.yapnplay.backend.contract.transport

/**
 * The contract version. Any change bumps this, is written in
 * docs/backend-api-contract.md, and the golden files change with it.
 *
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
const val CONTRACT_VERSION: String = "0.11.0"
