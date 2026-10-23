package com.kglabs28.yapnplay.backend.contract

import kotlinx.serialization.Serializable

// ══════════════════════════════════════════════════════════════════════════
// HTTP request/response bodies (REST API, not WebSocket)
// ══════════════════════════════════════════════════════════════════════════

/** Response after creating a new guest account. */
@Serializable
data class GuestCreated(
    val userId: String,
    /** Secret token used as the WebSocket bearer credential. */
    val secret: String,
)

/** Response with the current user's identity. */
@Serializable
data class Me(
    val userId: String,
    val displayName: String,
    /** Server-computed from all match results (random and room matches). */
    val xp: Int = 0,
    /** Derived from [xp] by the server. */
    val level: Int = 1,
    /** 0.4.5: the player's coins (play money), kept by the server. */
    val coins: Int = 0,
    /** Provisional: economy not designed yet. */
    val gems: Int = 0,
    /** The user's 6-character ID that friends type to add them (unique). */
    val userCode: String = "",
    /** One of the app's preset avatars ("" = default). */
    val avatarId: String = "",
    val gamesPlayed: Int = 0,
    val gamesWon: Int = 0,
    /** 0.4.5: XP where the current level starts and where the next begins (progress bar). */
    val levelXp: Int = 0,
    val nextLevelXp: Int = 1_000,
    /** 0.4.5: coins the daily bonus just added on this call (0 if already given today): show a "+100" toast. */
    val dailyBonus: Int = 0,
)

/** Provisional (post-POC friends): the user's friends, online first. */
@Serializable
data class Friends(
    val friends: List<FriendSummary>,
)

@Serializable
data class FriendSummary(
    val userId: String,
    val displayName: String,
    val online: Boolean,
    val userCode: String = "",
    val avatarId: String = "",
)

/**
 * What a room looks like before joining (`GET /rooms/preview/{code}`),
 * for the "Join this room?" dialog from an invite link or a typed code.
 */
@Serializable
data class RoomPreview(
    val roomId: String,
    val code: String,
    val name: String,
    val description: String? = null,
    val memberCount: Int,
    val onlineCount: Int,
    /** True when the caller (from the connection) is already a member. */
    val isMember: Boolean = false,
)

/** Registers this device for push (`POST /devices`); token is the FCM token. */
@Serializable
data class RegisterDevice(
    val token: String,
    val platform: String,
)

/** How many users are connected right now (Game Zone "1248 Online"). */
@Serializable
data class OnlineStats(
    val online: Int,
)

/** Request to update the display name. */
@Serializable
data class UpdateName(
    val displayName: String,
)

/** Response with the list of rooms the user belongs to. */
@Serializable
data class MyRooms(
    val rooms: List<RoomSummary>,
)

@Serializable
data class RoomSummary(
    val roomId: String,
    val code: String,
    val name: String,
    val memberCount: Int,
    /** Members present right now — shown as "N live". */
    val onlineCount: Int,
    /** Whether the requesting user currently leads this room. */
    val isLeader: Boolean = false,
    /** Room badge: group, dice, family or gamepad (unknown → group). */
    val icon: String? = null,
)

/** Response with a voice provider connection token. */
@Serializable
data class VoiceToken(
    /** WebSocket URL for the voice provider. */
    val url: String,
    /** Access token for the voice provider. */
    val token: String,
    /** Expiry time in epoch milliseconds. */
    val expiresAt: Long,
)

/** Answer to `JoinRoom(code)` from someone who is not a member yet. */
@Serializable
enum class JoinStatus { JOINED, PENDING_APPROVAL }

/**
 * `JoinRoom(code)` result: [JoinStatus.JOINED] with [room] for a member;
 * otherwise [JoinStatus.PENDING_APPROVAL] — the request sits in the
 * admin's Waiting List and the user is notified once approved.
 */
@Serializable
data class JoinRoomResult(
    val status: JoinStatus,
    val roomName: String,
    val room: RoomSummary? = null,
)

/** `PATCH /me`: change the user's name or avatar (null = keep as is). */
@Serializable
data class UpdateProfileBody(
    val displayName: String? = null,
    val avatarId: String? = null,
)

/** `GET /friends/requests`: friend requests other people sent the user, newest first. */
@Serializable
data class FriendRequests(
    val requests: List<FriendRequestSummary>,
)

@Serializable
data class FriendRequestSummary(
    val requestId: String,
    val from: FriendSummary,
)

/** `POST /friends/requests`: ask the person whose ID is [userCode] to be friends. */
@Serializable
data class SendFriendRequestBody(
    val userCode: String = "",
    /** Instead of the ID: someone the user can see by id (a room member card). */
    val userId: String? = null,
)

@Serializable
data class SendFriendRequestReply(
    val outcome: FriendRequestOutcomeCode,
)

/**
 * What became of a friend request: sent; nobody has that ID; it's the
 * user's own ID; already friends; already asked; the user blocked them;
 * or they had already asked the user, so now they're friends.
 */
@Serializable
enum class FriendRequestOutcomeCode { SENT, NOT_FOUND, SELF, ALREADY_FRIENDS, ALREADY_REQUESTED, BLOCKED, ACCEPTED }

/** `POST /blocks`: block a person (also ends any friendship and pending request). */
@Serializable
data class BlockUserBody(
    val userId: String,
)

/** `GET /blocks`: people the user blocked, newest first. Unblock with `DELETE /blocks/{userId}`. */
@Serializable
data class BlockedUsers(
    val users: List<BlockedUserSummary>,
)

@Serializable
data class BlockedUserSummary(
    val userId: String,
    val displayName: String,
    val avatarId: String = "",
    /** When the user blocked them (epoch millis). */
    val blockedAt: Long,
)

/**
 * `GET /matches/active`: the unfinished match the caller still holds a seat
 * in (they closed the app or lost the connection), so the app can offer to
 * rejoin. 204 No Content when there is none (or they chose Leave).
 */
@Serializable
data class ActiveMatch(
    val matchId: String,
    val gameId: String,
    /** Room name, or "Quick Play". */
    val title: String,
    val roomId: String? = null,
)

/** `POST /rooms/{roomId}/invites`: friends to invite to the room. */
@Serializable
data class InviteToRoomBody(
    val userIds: List<String> = emptyList(),
    /** 0.9.0: 6-character player codes (friends or not); unknown codes come back in `skipped` as the code. */
    val userCodes: List<String> = emptyList(),
)

/** Who got an invite, and who was skipped (not a friend, blocked, already a member or invited, room full). */
@Serializable
data class InviteToRoomReply(
    val invited: List<String>,
    val skipped: List<String> = emptyList(),
)

/** `GET /rooms/invites`: room invites waiting for the user, newest first. */
@Serializable
data class RoomInvites(
    val invites: List<RoomInviteSummary>,
)

@Serializable
data class RoomInviteSummary(
    val inviteId: String,
    val roomId: String,
    val roomName: String,
    /** Room badge key, same as RoomSummary.icon. */
    val roomIcon: String = "group",
    val memberCount: Int,
    val invitedBy: FriendSummary,
    /** Epoch millis. */
    val invitedAt: Long,
)

/** `POST /auth/play-games` (0.4.3): the Play Games server auth code from the app. */
@Serializable
data class PlayGamesSignInBody(
    val serverAuthCode: String,
)

/**
 * The account to use from now on (save it, replacing any guest). [restored]
 * true: a different account, already linked to this Play Games player, came
 * back (new phone or reinstall); its secret is new.
 */
@Serializable
data class PlayGamesSignedIn(
    val userId: String,
    val secret: String,
    val restored: Boolean,
)

/** 0.4.5 `POST /coins/ad-reward`: after a fully watched rewarded ad. [coins] is the new balance. */
@Serializable
data class CoinsReward(
    val coins: Int,
    val granted: Int,
    val adsLeftToday: Int,
    /** 0.5.0: rewarded ads allowed per day. */
    val dailyLimit: Int = 0,
    /** 0.5.0: when the next ad can pay (epoch ms); null = now. */
    val nextAdAt: Long? = null,
    /** 0.7.0: the whole day's ladder in order ([granted] is the step just paid). */
    val adRewards: List<Int> = emptyList(),
)

/**
 * 0.5.0 `GET /coins/ad-status`: what a rewarded ad pays, today's limit and
 * what's left, and when the next one can pay ([nextAdAt] epoch ms; null =
 * now; after the daily limit, the start of the next India-time day).
 */
@Serializable
data class AdRewardStatus(
    /** What the next ad pays (0 when none are left today). */
    val coinsPerAd: Int,
    val dailyLimit: Int,
    val adsLeftToday: Int,
    val nextAdAt: Long?,
    val serverTime: Long,
    /** 0.7.0: the whole day's ladder in order, e.g. [200, 300, 400, 500, 700]. */
    val adRewards: List<Int> = emptyList(),
)

/** 0.4.5 `GET /coins/leagues`: Quick Play leagues; [canEnter] is false when [coins] are short of the fee. */
@Serializable
data class Leagues(
    val coins: Int,
    val leagues: List<League>,
)

@Serializable
data class League(
    val entryCoins: Int,
    val name: String,
    val canEnter: Boolean,
)

/** 0.5.0 `POST /coins/ad-reward` body: a unique id per ad view (a random UUID, or AdMob's transaction id). */
@Serializable
data class AdRewardBody(
    val adId: String,
)

/**
 * `GET /matches/history`: the caller's latest matches, newest first, each
 * with every seat as the result showed it (place, coins, XP), so the app
 * can list them and open each one's leaderboard again.
 */
@Serializable
data class MatchHistoryResponse(val matches: List<MatchHistoryItem>)

@Serializable
data class MatchHistoryItem(
    val matchId: String,
    val gameId: String,
    val roomMatch: Boolean,
    val startedAt: Long,
    val endedAt: Long,
    /** The caller's own seat. */
    val mySeat: Int,
    val seats: List<MatchHistorySeat>,
)

/** One seat of a past match. [userId] is null for a CPU; coins: the pot share, the finishing coins and the entry staked. */
@Serializable
data class MatchHistorySeat(
    val seat: Int,
    val userId: String? = null,
    val name: String,
    val avatarId: String = "",
    val place: Int,
    val coinsWon: Int = 0,
    val completionCoins: Int = 0,
    val stake: Int = 0,
    val xp: Int = 0,
)
