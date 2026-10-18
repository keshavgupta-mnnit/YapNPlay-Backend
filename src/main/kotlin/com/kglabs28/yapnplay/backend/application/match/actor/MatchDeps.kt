package com.kglabs28.yapnplay.backend.application.match.actor

import com.kglabs28.yapnplay.backend.application.match.MatchLifecycleListener
import com.kglabs28.yapnplay.backend.application.match.RoomGateway
import com.kglabs28.yapnplay.backend.application.realtime.ClientNotifier
import com.kglabs28.yapnplay.backend.application.realtime.EnvelopeCodec
import com.kglabs28.yapnplay.backend.domain.common.Clock

/** What every match actor needs, shared by all matches. */
class MatchDeps(
    val notifier: ClientNotifier,
    val codec: EnvelopeCodec,
    val clock: Clock,
    val rooms: RoomGateway,
    val listener: MatchLifecycleListener,
)
