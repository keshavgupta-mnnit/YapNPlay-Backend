package com.kglabs28.yapnplay.backend.application.match.games.doodleking

import com.kglabs28.yapnplay.backend.config.BackendConstants
import com.kglabs28.yapnplay.backend.contract.doodleking.DoodleFeedEntry
import com.kglabs28.yapnplay.backend.contract.doodleking.DoodleKingPublicState
import com.kglabs28.yapnplay.backend.contract.doodleking.DoodleKingWireEvent
import com.kglabs28.yapnplay.backend.contract.doodleking.DoodleSeatState
import com.kglabs28.yapnplay.backend.games.base.play.GameEvent
import com.kglabs28.yapnplay.backend.games.doodleking.DoodleKingHints
import com.kglabs28.yapnplay.backend.games.doodleking.DoodleKingState
import com.kglabs28.yapnplay.backend.games.doodleking.DoodlePhase
import com.kglabs28.yapnplay.backend.games.doodleking.DoodleRightGuess
import com.kglabs28.yapnplay.backend.games.doodleking.DoodleTurnEnded
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * Doodle King's state as one viewer may see it: the word only for the drawer
 * (and everyone once it's revealed), the 3 choices only for the drawer, and
 * only the feed lines meant for them.
 */
class DoodleKingWireMapper(private val json: Json) {

    fun publicState(s: DoodleKingState, viewerSeat: Int?, strokeCount: Int): JsonObject {
        val isDrawer = viewerSeat == s.drawer && s.phase != DoodlePhase.FINISHED
        val wordShown = isDrawer || s.phase == DoodlePhase.REVEAL || s.phase == DoodlePhase.FINISHED
        val wire = DoodleKingPublicState(
            phase = s.phase.name,
            round = minOf(s.round, s.dkRules.mandatory.rounds),
            rounds = s.dkRules.mandatory.rounds,
            level = s.level.name,
            drawerSeat = s.drawer,
            phaseEndsAt = s.phaseEndsAt,
            drawSeconds = (s.dkRules.mandatory.drawMs / BackendConstants.MILLIS_PER_SECOND).toInt(),
            word = s.word?.takeIf { wordShown },
            options = if (isDrawer && s.phase == DoodlePhase.PICK) s.options else emptyList(),
            hint = s.word?.let { DoodleKingHints.mask(it, s.hintOrder.take(s.hintsShown)) }.orEmpty(),
            seats = (1..s.seatCount).map { seat ->
                DoodleSeatState(seat, s.scores[seat] ?: 0, seat in s.guessed, s.turnPoints[seat] ?: 0, seat in s.removed)
            },
            feed = s.feed.filter { it.visibleTo == null || viewerSeat in it.visibleTo }
                .map { DoodleFeedEntry(it.kind.name, it.seat, it.text, it.points) },
            drawingId = s.drawingId,
            strokeCount = strokeCount,
        )
        return json.encodeToJsonElement(DoodleKingPublicState.serializer(), wire).jsonObject
    }

    fun events(events: List<GameEvent>): List<JsonObject> = events.mapNotNull { event ->
        when (event) {
            is DoodleRightGuess -> DoodleKingWireEvent.Guessed(event.seat, event.points)
            is DoodleTurnEnded -> DoodleKingWireEvent.Revealed(event.word, event.points)
            else -> null
        }?.let { json.encodeToJsonElement(DoodleKingWireEvent.serializer(), it).jsonObject }
    }
}
