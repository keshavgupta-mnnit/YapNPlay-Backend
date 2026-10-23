package com.kglabs28.yapnplay.backend.contract

import com.kglabs28.yapnplay.backend.games.elements.board.protocol.LegalMove
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

// ══════════════════════════════════════════════════════════════════════════
// Match channel — client to server requests
// (Mechanic-specific requests like RollDice/MovePiece live in
// games/elements/board/protocol — this file holds only generic messages.)
// ══════════════════════════════════════════════════════════════════════════

/**
 * Ask for the current snapshot: on opening the match screen, after a
 * reconnect, or (a room member without a seat) to start watching.
 */
@Serializable
data class MatchRequestSnapshot(
    val matchId: String,
    val lastVersion: Long,
)

/** A game-specific action; [action] carries `"type"` plus its fields (e.g. `{"type":"BuildOnTile","tile":7}`). */
@Serializable
data class GameAction(
    val matchId: String,
    val action: JsonObject,
)

/** The seat's owner turns Autoplay on (the server plays their turns) or off. */
@Serializable
data class SetAutoplay(
    val matchId: String,
    val enabled: Boolean,
)

/** The user's mic switch at this table (shown on their seat or spectator chip). */
@Serializable
data class SetMic(
    val matchId: String,
    val micOn: Boolean,
)

// ══════════════════════════════════════════════════════════════════════════
// Match channel — server to client responses
// ══════════════════════════════════════════════════════════════════════════

/**
 * Full match state for one viewer, sent after every change. [events] are
 * what happened since this viewer's previous snapshot, in order, so the
 * table can animate them before showing the final state.
 */
@Serializable
data class MatchSnapshot(
    val matchId: String,
    val version: Long,
    /** Server time in epoch milliseconds. */
    val serverTime: Long,
    /** Who is viewing: player with seat, or spectator. */
    val viewer: ViewerInfo,
    val seats: List<SeatInfo>,
    /** Game-specific public state as JSON (`RoyalLudoState`, `CityBazaarState` wire shapes). */
    val publicState: JsonObject,
    /** Legal moves for the viewer when it is their turn; empty otherwise. */
    val legalMoves: List<LegalMove>,
    val turn: TurnInfo,
    val gameId: String = "",
    /** Room name, or "Quick Play". */
    val title: String = "",
    /** The room this match belongs to; null for Quick Play (no spectators, no handover). */
    val roomId: String? = null,
    /** When the match began (epoch millis). */
    val startedAt: Long = 0,
    val spectators: List<SpectatorInfo> = emptyList(),
    /** A seat changing hands; null when none. */
    val seatOffer: SeatOfferInfo? = null,
    /** Game-specific events (each with `"type"`), oldest first. */
    val events: List<JsonObject> = emptyList(),
    /** 0.4.4: the keep-playing vote while the game waits at its round or time limit; null otherwise. */
    val extensionVote: ExtensionVoteInfo? = null,
    /** 0.5.0: the coin pot (null for free games). */
    val pot: MatchPot? = null,
    /** 0.5.0: the end-game vote while it runs (play is paused). */
    val endVote: EndVoteInfo? = null,
)

/** [total] = entryCoins x (paid players + CPU seats); [split] = % of the pot by place (1st first). */
@Serializable
data class MatchPot(
    val entryCoins: Int,
    val total: Int,
    val split: List<Int>,
)

/**
 * 0.4.4, any game that ends on a round or time limit: with 2+ players still in
 * there is no clear winner, so [voters] (humans the server isn't playing for)
 * choose to play on. Closes at [endsAt] (epoch millis) or once everyone voted;
 * continue when at least half say yes (a missing vote counts as "end").
 * Going on adds [extraRounds] or [extraMinutes] (whichever limit was hit).
 * The result arrives as the event `{"type":"ExtensionVoted","continued":…,"yes":…,"voters":…}`.
 */
@Serializable
data class ExtensionVoteInfo(
    val endsAt: Long,
    val voters: List<Int>,
    val yes: List<Int> = emptyList(),
    val no: List<Int> = emptyList(),
    val extraRounds: Int? = null,
    val extraMinutes: Int? = null,
)

/** 0.4.4: a voter's choice at the limit: keep playing (true) or end now. Once per voter. */
@Serializable
data class VoteExtension(
    val matchId: String,
    val keepPlaying: Boolean,
)

/**
 * 0.5.0: someone asked to end the match early. Play is paused (no turn
 * deadline) until it is decided. [voters] are the humans still in; the
 * proposer counts as yes. More than half yes ends the match (ranked like
 * the time limit); a missing vote counts as no. The result arrives as the
 * event `{"type":"EndVoted","ended":…,"yes":…,"voters":…}`.
 */
@Serializable
data class EndVoteInfo(
    val proposerSeat: Int,
    val endsAt: Long,
    val voters: List<Int>,
    val yes: List<Int> = emptyList(),
    val no: List<Int> = emptyList(),
)

/** 0.5.0: ask everyone to end the match early (a player at the table, not more often than the cooldown). */
@Serializable
data class ProposeEndMatch(
    val matchId: String,
)

/** 0.5.0: a vote on ending the match early. Once per voter. */
@Serializable
data class CastEndVote(
    val matchId: String,
    val agree: Boolean,
)

/** 0.5.0: the generic event sent when the end-game vote is decided. */
@Serializable
data class EndVoted(
    val ended: Boolean,
    val yes: Int,
    val voters: Int,
    val type: String = "EndVoted",
)

/** 0.4.4: the generic event sent when the keep-playing vote is decided. */
@Serializable
data class ExtensionVoted(
    val continued: Boolean,
    val yes: Int,
    val voters: Int,
    val type: String = "ExtensionVoted",
)

@Serializable
data class ViewerInfo(
    val role: ViewerRole,
    /** Seat number if viewer is a player, null if spectator. */
    val seat: Int? = null,
)

enum class ViewerRole {
    PLAYER,
    SPECTATOR,
}

@Serializable
data class SeatInfo(
    val seat: Int,
    val colour: Colour,
    val controllerUserId: String,
    val status: MatchSeatStatus,
    val connected: Boolean,
    val autoPlay: Boolean,
    val consecutiveMisses: Int,
    /** True when a CPU fills this seat (Quick Play fill); controllerUserId is then a cpu id. */
    val cpu: Boolean = false,
    val displayName: String = "",
    val level: Int = 1,
    val micOn: Boolean = false,
    val presence: SeatPresence = SeatPresence.PLAYING,
    /** Preset avatar of whoever sits here (a CPU's robot avatar); "" = default icon. */
    val avatarId: String = "",
)

enum class Colour {
    RED,
    GREEN,
    YELLOW,
    BLUE,
    PURPLE,
    ORANGE,
}

/** At the table, away (the server plays their turns at once) or handed to the bot. */
enum class SeatPresence {
    PLAYING,
    AWAY,
    BOT,
}

enum class MatchSeatStatus {
    ACTIVE,
    REMOVED,
}

/** Someone watching a room match. */
@Serializable
data class SpectatorInfo(
    val userId: String,
    val displayName: String,
    val micOn: Boolean = false,
    /** Their avatar preset id ("" for none). */
    val avatarId: String = "",
)

/**
 * A seat changing hands (see the contract's seat handover section).
 * [userId]/[name] are the person the stage is about: the absent owner,
 * the named spectator, or the deciding leader. [endsAt] epoch millis.
 */
@Serializable
data class SeatOfferInfo(
    val seat: Int,
    val leaverName: String,
    val stage: SeatOfferStageName,
    val endsAt: Long,
    val userId: String? = null,
    val name: String? = null,
    /** 1: the leaver's pick, 2: the leader's pick (TO_SPECTATOR only). */
    val round: Int? = null,
)

enum class SeatOfferStageName {
    CONFIRMING_ABSENCE,
    TO_SPECTATOR,
    LEADER_CHOOSING,
    OPEN_TO_ALL,
}

/** A single game event with its version (for the event log). */
@Serializable
data class MatchEvent(
    val version: Long,
    /** Game-specific event as JSON. */
    val event: JsonObject,
)

/** Sent when the match ends with the final ranking. */
@Serializable
data class MatchEnded(
    val ranking: List<RankedSeat>,
)

@Serializable
data class RankedSeat(
    val seat: Int,
    val status: MatchSeatStatus,
    /** 1-based finishing place. */
    val place: Int,
    /**
     * 0.5.0: coins this place wins from the pot (0 in free games). 0.10.0:
     * shown for every seat as if a person held it; only a paying human still
     * at the table is credited (a CPU's or leaver's share goes to nobody).
     */
    val coinsWon: Int = 0,
    /** 0.5.0: coins for finishing the match (0 for leavers, bot-held seats, short matches or past the daily cap). 0.10.0: CPUs show the same amount (display only). */
    val completionCoins: Int = 0,
    /** 0.5.0: XP earned (place and match length; 0 for CPUs and leavers). */
    val xpGained: Int = 0,
    /** 0.10.0: the entry this seat put in (the bank's for a CPU; 0 in free games or when it didn't pay). */
    val stake: Int = 0,
)

// ══════════════════════════════════════════════════════════════════════════
// Shared types
// ══════════════════════════════════════════════════════════════════════════

/** The current turn: whose seat, what phase, and when the deadline is. */
@Serializable
data class TurnInfo(
    val seat: Int,
    /** Game-specific phase (e.g., "ROLL" or "MOVE" for Royal Ludo). */
    val phase: String,
    /** Absolute deadline in epoch milliseconds; null while play is on hold or finished. */
    val deadline: Long?,
)
