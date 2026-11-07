package com.kglabs28.yapnplay.backend.application.match.games.cyberblast

import com.kglabs28.yapnplay.backend.contract.cyberblast.CyberBlastPublicState
import com.kglabs28.yapnplay.backend.contract.cyberblast.CyberBlastWireEvent
import com.kglabs28.yapnplay.backend.contract.cyberblast.CyberClientTuning
import com.kglabs28.yapnplay.backend.contract.cyberblast.CyberBombWire
import com.kglabs28.yapnplay.backend.contract.cyberblast.CyberFlameWire
import com.kglabs28.yapnplay.backend.contract.cyberblast.CyberFrame
import com.kglabs28.yapnplay.backend.contract.cyberblast.CyberHitWire
import com.kglabs28.yapnplay.backend.contract.cyberblast.CyberItemWire
import com.kglabs28.yapnplay.backend.contract.cyberblast.CyberPlayerWire
import com.kglabs28.yapnplay.backend.games.base.play.GameEvent
import com.kglabs28.yapnplay.backend.games.cyberblast.BlastBoom
import com.kglabs28.yapnplay.backend.games.cyberblast.BlastClosing
import com.kglabs28.yapnplay.backend.games.cyberblast.BlastHit
import com.kglabs28.yapnplay.backend.games.cyberblast.BlastOut
import com.kglabs28.yapnplay.backend.games.cyberblast.BlastPhase
import com.kglabs28.yapnplay.backend.games.cyberblast.CyberBlastState
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/** Cyber Blast's state and events → wire JSON (frames, the snapshot's public state, events). */
class CyberBlastWireMapper(private val json: Json) {

    /** One frame; the blockers and walls only when [withCrates] / [withWalls] (changed, or a snapshot). [events]: what happened since the last frame. */
    fun frame(state: CyberBlastState, number: Long, events: List<GameEvent>, withCrates: Boolean, withWalls: Boolean): CyberFrame {
        val now = state.time
        val booms = events.filterIsInstance<BlastBoom>()
        return CyberFrame(
            frame = number,
            time = now,
            phase = state.phase.name,
            countdownMs = if (state.phase == BlastPhase.COUNTDOWN) (state.mandatory.countdownMs - now).coerceAtLeast(0) else 0,
            closingInMs = BlastClosing.startsIn(state),
            players = state.players.map { p ->
                CyberPlayerWire(
                    seat = p.seat,
                    x = p.x,
                    y = p.y,
                    facing = p.facing.name,
                    moving = p.held != null && p.inPlay,
                    lives = p.lives,
                    score = p.score,
                    radius = p.radius,
                    boostMs = (p.boostUntil - now).coerceAtLeast(0),
                    shieldMs = (p.shieldUntil - now).coerceAtLeast(0),
                    out = !p.inPlay,
                    kills = p.hits,
                    streak = p.streak,
                )
            },
            bombs = state.bombs.map { CyberBombWire(it.id, it.tile, it.owner, it.radius, (it.explodesAt - now).coerceAtLeast(0)) },
            flames = state.flames.map { CyberFlameWire(it.tile, it.owner, it.shape.name, (it.endsAt - now).coerceAtLeast(0)) },
            items = state.items.map { CyberItemWire(it.tile, it.kind.name, (it.expiresAt - now).coerceAtLeast(0)) },
            crates = if (withCrates) state.crates.sorted() else null,
            walls = if (withWalls) state.walls.sorted() else null,
            hits = events.filterIsInstance<BlastHit>().map { it.wire() },
            broken = booms.flatMap { it.crates },
            booms = booms.flatMap { it.bombs },
        )
    }

    fun publicState(state: CyberBlastState, frame: CyberFrame, frameMs: Long, tuning: CyberClientTuning): JsonObject = json.encodeToJsonElement(
        CyberBlastPublicState.serializer(),
        CyberBlastPublicState(
            cols = state.cols,
            rows = state.rows,
            lives = state.mandatory.lives,
            frameMs = frameMs,
            fuseMs = state.mandatory.fuseMs,
            boostMs = state.mandatory.boostMs,
            miniMap = state.blastRules.house.miniMap,
            speed = state.mandatory.speed,
            boostSpeed = state.mandatory.boostSpeed,
            tuning = tuning,
            frame = frame,
        ),
    ).jsonObject

    fun events(events: List<GameEvent>): List<JsonObject> = events.mapNotNull { e ->
        when (e) {
            is BlastHit -> CyberBlastWireEvent.Hit(e.wire())
            is BlastOut -> CyberBlastWireEvent.Out(e.seat)
            else -> null
        }?.let { json.encodeToJsonElement(CyberBlastWireEvent.serializer(), it).jsonObject }
    }

    private fun BlastHit.wire() = CyberHitWire(victim, by, livesLeft, victimPoints, byPoints, self, crushed)
}
