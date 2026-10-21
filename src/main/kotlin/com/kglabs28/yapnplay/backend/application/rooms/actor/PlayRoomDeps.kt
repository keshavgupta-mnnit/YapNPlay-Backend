package com.kglabs28.yapnplay.backend.application.rooms.actor

import com.kglabs28.yapnplay.backend.application.realtime.ClientNotifier
import com.kglabs28.yapnplay.backend.application.realtime.EnvelopeCodec
import com.kglabs28.yapnplay.backend.application.rooms.GameCatalog
import com.kglabs28.yapnplay.backend.application.rooms.RoomPresence
import com.kglabs28.yapnplay.backend.domain.common.Clock
import com.kglabs28.yapnplay.backend.domain.rooms.MatchStarter
import com.kglabs28.yapnplay.backend.domain.rooms.PlayRoomRepository
import com.kglabs28.yapnplay.backend.domain.rooms.JoinRequestRepository
import com.kglabs28.yapnplay.backend.application.social.FriendDirectory
import com.kglabs28.yapnplay.backend.application.moderation.BlockChecker
import com.kglabs28.yapnplay.backend.application.push.RoomActivityNotifier
import com.kglabs28.yapnplay.backend.domain.rooms.RolePolicy
import com.kglabs28.yapnplay.backend.domain.rooms.StartPolicy

/** What every room actor needs, built once by Koin and shared by all rooms. */
class PlayRoomDeps(
    val repository: PlayRoomRepository,
    val notifier: ClientNotifier,
    val presence: RoomPresence,
    val games: GameCatalog,
    val codec: EnvelopeCodec,
    val startPolicy: StartPolicy,
    val rolePolicy: RolePolicy,
    val matchStarter: MatchStarter,
    val clock: Clock,
    val joinRequests: JoinRequestRepository,
    val friends: FriendDirectory,
    val blocks: BlockChecker,
    val activity: RoomActivityNotifier,
    val maxRoomsPerUser: Int,
    val countdownSeconds: Int,
)
