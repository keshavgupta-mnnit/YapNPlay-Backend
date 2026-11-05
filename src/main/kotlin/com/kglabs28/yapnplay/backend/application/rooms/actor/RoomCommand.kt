package com.kglabs28.yapnplay.backend.application.rooms.actor

import kotlinx.coroutines.CompletableDeferred

/** One item in a room actor's inbox. Small hierarchy, kept together. */
sealed interface RoomCommand {
    data class Request(
        val userId: String,
        val action: RoomAction,
        val reply: CompletableDeferred<RoomResult>,
    ) : RoomCommand

    /** The countdown that ends at [deadline] reached zero. Ignored if it was cancelled or replaced. */
    data class CountdownElapsed(val deadline: Long) : RoomCommand

    /** A temporary room's grace after its lobby emptied is over: delete it if still nobody is in. */
    data object DesertedCheck : RoomCommand
}
