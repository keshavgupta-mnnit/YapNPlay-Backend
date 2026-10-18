package com.kglabs28.yapnplay.backend.contract.transport

/**
 * The two logical channels carried by one WebSocket connection.
 */
enum class Channel {
    /** Room membership, readiness, countdown. */
    ROOM,

    /** Game-specific match events. */
    MATCH,

    /** Public Quick Play search (JoinQueue, QueueStatus, MatchFound). */
    MATCHMAKING,

    /** Voice-chat reports and votes (ReportPlayer, CastReportVote, …). */
    MODERATION,
}
