package com.kglabs28.yapnplay.backend.application.rooms.actor

import com.kglabs28.yapnplay.backend.config.BackendConstants
import com.kglabs28.yapnplay.backend.contract.ErrorCode
import com.kglabs28.yapnplay.backend.application.common.ServiceException
import com.kglabs28.yapnplay.backend.config.BackendStrings

/**
 * The lobby controls: game choice, house rules, bot fill, ready/watching,
 * voice switches and role hand-offs (decisions.md "room lobby").
 */
class LobbyHandler(
    private val state: PlayRoomState,
    private val deps: PlayRoomDeps,
    private val guards: RoomGuards,
    private val countdown: CountdownHandler,
) {
    /** Any change resets everyone's ready/watching and loads the game's default house rules. */
    suspend fun selectGame(userId: String, gameId: String?) {
        guards.requireLeader(userId)
        if (gameId != null && deps.games.findEnabled(gameId) == null) {
            throw ServiceException(ErrorCode.GAME_UNAVAILABLE, BackendStrings.GAME_UNAVAILABLE)
        }
        countdown.cancel()
        state.room = state.room.copy(selectedGame = gameId)
        state.houseRules = deps.games.defaultHouseRules(gameId)
        state.updateAll { copy(ready = false, readyAt = null, watching = false) }
        deps.repository.updateGame(state.room.id, gameId)
    }

    /** The leader picks the room's league; kept with the room. Never one someone in the lobby can't pay. */
    suspend fun setLeague(userId: String, entryCoins: Int) {
        guards.requireLeader(userId)
        if (entryCoins !in deps.leagueFees()) throw ServiceException(ErrorCode.BAD_REQUEST, BackendStrings.UNKNOWN_LEAGUE)
        val coins = deps.coins
        if (coins != null && state.members.values.filter { it.online }.any { coins.balance(it.userId) < entryCoins }) {
            throw ServiceException(ErrorCode.BAD_REQUEST, BackendStrings.LEAGUE_UNAFFORDABLE)
        }
        state.room = state.room.copy(entryCoins = entryCoins)
        deps.repository.updateEntryCoins(state.room.id, entryCoins)
    }

    fun setHouseRule(userId: String, key: String, enabled: Boolean) {
        guards.requireLeader(userId)
        val game = guards.requireGame()
        if (game.houseRuleOptions.none { it.key == key }) {
            throw ServiceException(ErrorCode.BAD_REQUEST, BackendStrings.UNKNOWN_HOUSE_RULE)
        }
        state.houseRules = state.houseRules + (key to enabled)
    }

    /** The admin renames the room: trimmed, 1..ROOM_NAME_MAX_LENGTH characters, no control characters. */
    suspend fun rename(userId: String, name: String) {
        guards.requireAdmin(userId)
        val trimmed = name.trim()
        if (trimmed.isEmpty() || trimmed.length > BackendConstants.ROOM_NAME_MAX_LENGTH || trimmed.any { it.isISOControl() }) {
            throw ServiceException(ErrorCode.BAD_REQUEST, "Room name: 1 to ${BackendConstants.ROOM_NAME_MAX_LENGTH} characters")
        }
        state.room = state.room.copy(name = trimmed)
        deps.repository.updateName(state.room.id, trimmed)
    }

    suspend fun setBotFill(userId: String, enabled: Boolean) {
        guards.requireLeader(userId)
        state.room = state.room.copy(botFill = enabled)
        deps.repository.updateBotFill(state.room.id, enabled)
        countdown.revalidate()
    }

    suspend fun setReady(userId: String, ready: Boolean) {
        state.member(userId)
        guards.requireGame()
        if (ready) deps.blocks.requireNotBlocked(userId)
        val now = deps.clock.nowMillis()
        state.update(userId) {
            if (ready) copy(ready = true, readyAt = readyAt ?: now, watching = false) else copy(ready = false, readyAt = null)
        }
        countdown.revalidate()
    }

    fun setWatching(userId: String, watching: Boolean) {
        state.update(userId) { copy(watching = watching) }
    }

    fun setVoiceState(userId: String, micOn: Boolean, speakerOn: Boolean) {
        state.update(userId) { copy(micOn = micOn, speakerOn = speakerOn, talkingMuted = talkingMuted && !micOn) }
    }

    /** Talking with the mic off: shown to everyone as a grey voice line. */
    fun setTalkingMuted(userId: String, talking: Boolean) {
        state.update(userId) { copy(talkingMuted = talking) }
    }

    /** Only someone online can take over picking the game. */
    suspend fun transferLeader(userId: String, targetId: String) {
        guards.requireLeader(userId)
        guards.requireTarget(targetId)
        if (!state.members.getValue(targetId).online) {
            throw ServiceException(ErrorCode.BAD_REQUEST, BackendStrings.TARGET_OFFLINE)
        }
        state.room = state.room.copy(leaderId = targetId)
        deps.repository.updateRoles(state.room.id, targetId, state.room.adminId)
        countdown.revalidate()
    }

    suspend fun transferAdmin(userId: String, targetId: String) {
        guards.requireAdmin(userId)
        guards.requireTarget(targetId)
        state.room = state.room.copy(adminId = targetId)
        deps.repository.updateRoles(state.room.id, state.room.leaderId, targetId)
    }
}
