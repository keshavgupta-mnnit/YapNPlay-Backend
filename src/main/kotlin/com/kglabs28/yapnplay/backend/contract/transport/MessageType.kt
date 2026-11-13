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
    /** 0.12.4: the leader saves a Quick Play party as a Game Room (or adds newcomers to it). */
    const val SAVE_ROOM = "SaveRoom"
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
    /** 0.15.0: ask to lead (no payload; only while the admin is away). */
    const val REQUEST_LEADER = "RequestLeader"
    /** 0.15.0: the leader refuses the request (no payload). */
    const val DENY_LEADER_REQUEST = "DenyLeaderRequest"
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
    /** 0.12.3: a play-now invite to a Quick Play party (server push, ROOM channel). */
    const val PLAY_INVITE = "PlayInvite"
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
    const val REMOVE_BOT_SEAT = "RemoveBotSeat"
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
    /** 0.13.0 Cyber Blast: the player's steering and bombs (frames come back as MatchRelay `CyberFrame`). */
    const val CYBER_INPUT = "CyberInput"
    /** 0.11.0: send an emoji to a seat for coins (answered with EmojiGiftResult). */
    const val SEND_EMOJI_GIFT = "SendEmojiGift"

    // ── Match channel: server to client ───────────────────────────────────
    const val MATCH_SNAPSHOT = "MatchSnapshot"
    const val MATCH_EVENT = "MatchEvent"
    const val MATCH_ENDED = "MatchEnded"
    /** A side message forwarded as it is (a Doodle King stroke), with no full snapshot; the payload has its own `"type"`. */
    const val MATCH_RELAY = "MatchRelay"
    /** 0.11.0: reply to SendEmojiGift. */
    const val EMOJI_GIFT_RESULT = "EmojiGiftResult"
    /** 0.11.0: the `"type"` of a MatchRelay carrying an emoji one seat sent another. */
    const val EMOJI_GIFT = "EmojiGift"

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

    // ── Voice channel (0.16.0) ────────────────────────────────────────────
    const val JOIN_VOICE = "JoinVoice"
    /** No payload; Ack. */
    const val LEAVE_VOICE = "LeaveVoice"
    const val VOICE_PEERS = "VoicePeers"
    const val VOICE_SIGNAL = "VoiceSignal"
    /** About once a minute while relayed: TURN bytes since the last report; Ack. */
    const val VOICE_RELAY_USAGE = "VoiceRelayUsage"
    /** Push: the server closed the voice room (a kill switch); no payload. */
    const val VOICE_CLOSED = "VoiceClosed"
}
