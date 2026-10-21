package com.kglabs28.yapnplay.backend.contract.transport

/**
 * The contract version. Any change bumps this, is written in
 * docs/backend-api-contract.md, and the golden files change with it.
 *
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
const val CONTRACT_VERSION: String = "0.5.0"
