package com.kglabs28.yapnplay.backend.application.rooms.actor

/** Everything a user can ask a room to do. Small hierarchy, kept together. */
sealed interface RoomAction {
    /** A member enters; anyone else is put on the admin's Waiting List. */
    data class Join(val displayName: String, val avatarId: String = "") : RoomAction

    /**
     * Stop being present. With [force] false it is refused while the user
     * is about to play in this room's countdown (switching rooms).
     */
    data class Exit(val force: Boolean) : RoomAction

    data object Leave : RoomAction
    data class SelectGame(val gameId: String?) : RoomAction
    data class SetBotFill(val enabled: Boolean) : RoomAction
    data class SetHouseRule(val key: String, val enabled: Boolean) : RoomAction
    data class SetReady(val ready: Boolean) : RoomAction
    data class SetWatching(val watching: Boolean) : RoomAction
    data class SetVoiceState(val micOn: Boolean, val speakerOn: Boolean) : RoomAction
    data object StartCountdown : RoomAction
    data object CancelCountdown : RoomAction
    data class TransferLeader(val userId: String) : RoomAction
    data class TransferAdmin(val userId: String) : RoomAction
    data class RemoveMember(val userId: String) : RoomAction
    data object Snapshot : RoomAction
    /** The member changed their name and/or avatar (null = unchanged). */
    data class UpdateProfile(val displayName: String?, val avatarId: String?) : RoomAction
    data class ApproveJoin(val userId: String) : RoomAction
    data class DeclineJoin(val userId: String) : RoomAction

    /** The room's match ended (sent by the server, not a member). */
    data class MatchEnded(val matchId: String) : RoomAction

    /** Send the asking member a fresh snapshot (their friends changed). */
    data object Resend : RoomAction
}
