package com.kglabs28.yapnplay.backend.contract.transport

/**
 * Message type strings used in the Envelope.
 * The receiver uses these to deserialize the payload correctly.
 */
object MessageType {
    // ── Room channel: client to server ────────────────────────────────────
    const val CREATE_ROOM = "CreateRoom"
    const val JOIN_ROOM = "JoinRoom"
    const val LEAVE_ROOM = "LeaveRoom"
    const val EXIT_LOBBY = "ExitLobby"
    const val SET_READY = "SetReady"
    const val START_COUNTDOWN = "StartCountdown"
    const val CANCEL_COUNTDOWN = "CancelCountdown"
    const val REQUEST_SNAPSHOT = "RequestSnapshot"
    const val SELECT_GAME = "SelectGame"
    const val SET_BOT_FILL = "SetBotFill"
    const val SET_HOUSE_RULE = "SetHouseRule"
    const val SET_LEAGUE = "SetLeague"
    const val SET_WATCHING = "SetWatching"
    const val SET_VOICE_STATE = "SetVoiceState"
    const val TRANSFER_LEADER = "TransferLeader"
    const val TRANSFER_ADMIN = "TransferAdmin"
    const val REMOVE_MEMBER = "RemoveMember"
    const val RENAME_ROOM = "RenameRoom"
    const val APPROVE_JOIN = "ApproveJoin"
    const val DECLINE_JOIN = "DeclineJoin"

    // ── Room channel: server to client ────────────────────────────────────
    const val ACK = "Ack"
    /** Client to server on any channel: answered with Ack at once (the app times the round trip). */
    const val PING = "Ping"
    const val REJECTED = "Rejected"
    const val ROOM_SNAPSHOT = "RoomSnapshot"
    const val MATCH_STARTING = "MatchStarting"
    /** Reply to JoinRoom. */
    const val JOIN_ROOM_RESULT = "JoinRoomResult"
    /** Pushed to someone whose join request the admin approved. */
    const val JOIN_APPROVED = "JoinApproved"

    // ── Match channel: client to server ───────────────────────────────────
    const val ROLL_DICE = "RollDice"
    const val MOVE_PIECE = "MovePiece"
    const val GAME_ACTION = "GameAction"
    const val MATCH_REQUEST_SNAPSHOT = "MatchRequestSnapshot"
    const val SET_AUTOPLAY = "SetAutoplay"
    const val SET_MIC = "SetMic"
    /** Room and match channels: the user is talking with their mic off (others see a grey glow). */
    const val SET_TALKING_MUTED = "SetTalkingMuted"
    const val SET_PRESENCE = "SetPresence"
    const val CONFIRM_PRESENCE = "ConfirmPresence"
    const val LEAVE_SEAT = "LeaveSeat"
    const val ANSWER_SEAT_OFFER = "AnswerSeatOffer"
    const val CHOOSE_SEAT_TAKER = "ChooseSeatTaker"
    const val OFFER_BOT_SEAT = "OfferBotSeat"
    const val TAKE_SEAT = "TakeSeat"
    /** 0.4.4: keep playing at the limit, or end. */
    const val VOTE_EXTENSION = "VoteExtension"
    const val PROPOSE_END_MATCH = "ProposeEndMatch"
    const val CAST_END_VOTE = "CastEndVote"
    /** Doodle King: the drawer's stroke parts, undo and clear; anyone asking for the whole drawing. */
    const val DOODLE_STROKE = "DoodleStroke"
    const val DOODLE_UNDO = "DoodleUndo"
    const val DOODLE_CLEAR = "DoodleClear"
    const val DOODLE_SYNC = "DoodleSync"

    // ── Match channel: server to client ───────────────────────────────────
    const val MATCH_SNAPSHOT = "MatchSnapshot"
    const val MATCH_EVENT = "MatchEvent"
    const val MATCH_ENDED = "MatchEnded"
    /** A side message forwarded as it is (a Doodle King stroke), with no full snapshot; the payload has its own `"type"`. */
    const val MATCH_RELAY = "MatchRelay"

    // ── Matchmaking channel: client to server ─────────────────────────────
    const val JOIN_QUEUE = "JoinQueue"
    const val LEAVE_QUEUE = "LeaveQueue"

    // ── Matchmaking channel: server to client ─────────────────────────────
    const val QUEUE_STATUS = "QueueStatus"
    const val MATCH_FOUND = "MatchFound"
    const val QUEUE_CANCELLED = "QueueCancelled"

    // ── Moderation channel ────────────────────────────────────────────────
    const val REPORT_PLAYER = "ReportPlayer"
    const val CAST_REPORT_VOTE = "CastReportVote"
    const val REPORT_VOTE_REQUESTED = "ReportVoteRequested"
    const val REPORT_RESOLVED = "ReportResolved"
    const val PENALTY_ISSUED = "PenaltyIssued"
}
