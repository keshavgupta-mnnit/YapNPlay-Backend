package com.kglabs28.yapnplay.backend.contract

/**
 * Keys of the data push (FCM data message) the server sends when someone
 * enters a room. Shared contract: the server fills them, the app reads
 * them to show the notification and open that room's lobby on tap.
 */
object PushKeys {
    const val TYPE = "type"
    const val ROOM_ID = "roomId"
    const val ROOM_NAME = "roomName"
    const val TITLE = "title"
    const val BODY = "body"

    /** "Neha entered College Crew" / "5 players are in College Crew". */
    const val TYPE_ROOM_ACTIVITY = "room_activity"

    /** `RegisterDevice.platform` value for Android devices. */
    const val PLATFORM_ANDROID = "android"
}
