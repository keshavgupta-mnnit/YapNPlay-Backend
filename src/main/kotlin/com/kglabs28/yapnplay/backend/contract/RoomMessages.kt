package com.kglabs28.yapnplay.backend.contract

import com.kglabs28.yapnplay.backend.contract.ErrorCode
import kotlinx.serialization.Serializable

// ══════════════════════════════════════════════════════════════════════════
// Room channel — client to server requests
// ══════════════════════════════════════════════════════════════════════════

/** Create a new room. [name] is optional. */
@Serializable
data class CreateRoom(
    val name: String? = null,
    /** Badge: group, dice, family or gamepad (unknown → group). */
    val icon: String? = null,
    /** Friends to invite; at least one is required (0.4.2). */
    val inviteUserIds: List<String> = emptyList(),
)

/** Join a room by its six-character code. Also used to re-enter a room you already belong to. */
@Serializable
data class JoinRoom(
    val code: String,
)

/** Give up membership in the room. */
@Serializable
data object LeaveRoom

/** Stay a member but stop being present (go offline in the room). */
@Serializable
data object ExitLobby

/**
 * Leader picks the game for the next match (same picker as Game Zone).
 * null = "Change game": back to choosing. Any change resets every
 * member's ready/watching and loads the game's default house rules.
 */
@Serializable
data class SelectGame(
    val gameId: String? = null,
)

/**
 * Leader's room option, shown first in the house-rules section: when on,
 * bots fill empty seats only up to the selected game's minPlayers (never
 * beyond). When off, the match needs minPlayers ready humans.
 */
@Serializable
data class SetBotFill(
    val enabled: Boolean,
)

/** Leader turns one of the selected game's house rules on or off. */
@Serializable
data class SetHouseRule(
    val key: String,
    val enabled: Boolean,
)

/**
 * A member who has no seat (all seats taken) opts in or out of watching
 * the match as a spectator.
 */
@Serializable
data class SetWatching(
    val watching: Boolean,
)

/** Your mic/speaker switches, shown to everyone on your member card. */
@Serializable
data class SetVoiceState(
    val micOn: Boolean,
    val speakerOn: Boolean,
)

/** Leader hands the leader role (game choice, start) to [userId]. */
@Serializable
data class TransferLeader(
    val userId: String,
)

/** Admin hands the admin role (membership, waiting list) to [userId]. */
@Serializable
data class TransferAdmin(
    val userId: String,
)

/**
 * Admin removes [userId] from the room's membership (not just the lobby).
 * The removed user is told with a snapshot that no longer lists them.
 */
@Serializable
data class RemoveMember(
    val userId: String,
)

/** Admin lets [userId] (from the Waiting List) into the room. */
@Serializable
data class ApproveJoin(
    val userId: String,
)

/** Admin turns down [userId]'s join request. */
@Serializable
data class DeclineJoin(
    val userId: String,
)

/** Set your ready status. */
@Serializable
data class SetReady(
    val ready: Boolean,
)

/** Start the 10-second countdown to match start. */
@Serializable
data object StartCountdown

/** Cancel an in-progress countdown. */
@Serializable
data object CancelCountdown

/** Request the latest room snapshot (or a specific version). */
@Serializable
data class RequestSnapshot(
    val lastVersion: Long,
)

// ══════════════════════════════════════════════════════════════════════════
// Room channel — server to client responses
// ══════════════════════════════════════════════════════════════════════════

/** Acknowledges a request. */
@Serializable
data class Ack(
    val requestId: String,
)

/** Rejects a request with a typed error code. */
@Serializable
data class Rejected(
    val requestId: String,
    val code: ErrorCode,
    val message: String,
)

/** Full room state snapshot, sent after every room change. */
@Serializable
data class RoomSnapshot(
    val version: Long,
    /** Server time in epoch milliseconds. Deadlines are absolute server times. */
    val serverTime: Long,
    val room: RoomInfo,
)

@Serializable
data class RoomInfo(
    val id: String,
    /** Six-character join code. */
    val code: String,
    val name: String,
    val leaderId: String,
    /** Who manages membership (adds members, sees the waiting list). */
    val adminId: String? = null,
    /** Short room tagline ("Good vibes • Great games"). */
    val description: String? = null,
    /** Room badge key, same as RoomSummary.icon ("group", "dice", ...). */
    val icon: String? = null,
    val members: List<RoomMember>,
    /** Selected game ID, or null if none selected. */
    val selectedGame: String? = null,
    /** Leader's "fill with bots up to the minimum" option (see SetBotFill). */
    val botFill: Boolean = false,
    /** Selected game's house rules by key (see HouseRuleOption); reset on game change. */
    val houseRules: Map<String, Boolean> = emptyMap(),
    /** Countdown deadline in epoch millis, or null if no countdown running. */
    val countdownDeadline: Long? = null,
    /** Match ID if a match is in progress, or null. */
    val matchInProgress: String? = null,
    /** People asking to join; filled only in the admin's snapshot. */
    val waitingList: List<JoinRequestInfo> = emptyList(),
)

/** One join request in the admin's Waiting List. */
@Serializable
data class JoinRequestInfo(
    val userId: String,
    val displayName: String,
    val requestedAt: Long,
)

@Serializable
data class RoomMember(
    val userId: String,
    val displayName: String,
    val presence: Presence,
    val ready: Boolean,
    /** No seat free and opted in to watch the match. */
    val watching: Boolean = false,
    val micOn: Boolean = false,
    val speakerOn: Boolean = false,
    /** Relative to the receiving user: is this member their friend. */
    val isFriend: Boolean = false,
    /** When this member came online in the current session (epoch millis); leader succession uses it. */
    val onlineSince: Long? = null,
    /** Their preset avatar ("" = default); match tables look players up here by user id too. */
    val avatarId: String = "",
)

enum class Presence {
    ONLINE,
    OFFLINE,
}

/** Tells a requester the admin let them in; they can open the room now. */
@Serializable
data class JoinApproved(
    val roomId: String,
    val roomName: String,
)

/** Tells clients a match is about to start. */
@Serializable
data class MatchStarting(
    val matchId: String,
)
