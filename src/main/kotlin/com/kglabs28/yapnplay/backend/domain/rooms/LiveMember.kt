package com.kglabs28.yapnplay.backend.domain.rooms

/** One member as the room actor sees them right now. */
data class LiveMember(
    val userId: String,
    val displayName: String,
    val joinedAt: Long,
    val online: Boolean = false,
    /** When they came online this session; leader succession uses it. */
    val onlineSince: Long? = null,
    val ready: Boolean = false,
    /** When they pressed ready; seats go to the earliest. */
    val readyAt: Long? = null,
    val watching: Boolean = false,
    val micOn: Boolean = false,
    val speakerOn: Boolean = false,
)
