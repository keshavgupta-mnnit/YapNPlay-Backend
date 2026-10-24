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

    /** "Keshav invited you to Hostel Night Squad": tap opens the pending invites. */
    const val TYPE_ROOM_INVITE = "room_invite"
    const val INVITE_ID = "inviteId"

    /** "Neha sent you a friend request": tap opens the app. */
    const val TYPE_FRIEND_REQUEST = "friend_request"
    /** "Neha accepted your friend request": tap opens the app. */
    const val TYPE_FRIEND_ACCEPTED = "friend_accepted"
    /** The other player in a friend push. */
    const val USER_ID = "userId"

    /** `RegisterDevice.platform` value for Android devices. */
    const val PLATFORM_ANDROID = "android"
}
