package com.kglabs28.yapnplay.backend.application.match.actor

import com.kglabs28.yapnplay.backend.application.economy.MatchEconomy
import com.kglabs28.yapnplay.backend.domain.settings.Settings
import com.kglabs28.yapnplay.backend.application.match.MatchCheckpointStore
import com.kglabs28.yapnplay.backend.application.match.MatchLifecycleListener
import com.kglabs28.yapnplay.backend.application.match.MatchOutcomeStore
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
    /** Where running matches are saved for a restart. */
    val checkpoints: MatchCheckpointStore = MatchCheckpointStore.None,
    /** Every match's outcome (completed or aborted), for counting unfinished games. */
    val outcomes: MatchOutcomeStore = MatchOutcomeStore.None,
    /** Runtime settings (vote time); defaults in tests. */
    val settings: Settings = Settings.Defaults,
    /** Pays the pot and completion coins when a match ends. */
    val economy: MatchEconomy = MatchEconomy.None,
    val listener: MatchLifecycleListener,
)
