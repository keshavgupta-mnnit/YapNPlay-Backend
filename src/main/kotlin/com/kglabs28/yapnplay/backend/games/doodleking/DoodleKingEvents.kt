package com.kglabs28.yapnplay.backend.games.doodleking

import com.kglabs28.yapnplay.backend.games.base.play.Action
import com.kglabs28.yapnplay.backend.games.base.play.GameEvent

/** The game's clock: phases end, hints show. [away]: seats nobody is playing right now. */
data class DoodleTick(val now: Long, val away: Set<Int>) : Action
data class DoodlePick(val index: Int, val now: Long) : Action
data class DoodleGuess(val text: String, val now: Long) : Action
data class DoodlePass(val now: Long) : Action
/** The match ends now (an early-end vote, everyone gone, the table chose to stop), ranked as it stands. */
data object DoodleEnd : Action

/** The table voted to keep playing: [rounds] more, starting at once. */
data class DoodleExtend(val rounds: Int, val now: Long) : Action

sealed interface DoodleEvent : GameEvent

/** Something on the table changed (a phase, a hint, a feed line); nothing to animate. */
data object DoodleUpdated : DoodleEvent

/** [seat] guessed the word: +[points]. */
data class DoodleRightGuess(val seat: Int, val points: Int) : DoodleEvent

/** The turn ended: the word and each seat's points this turn. */
data class DoodleTurnEnded(val word: String, val points: Map<Int, Int>) : DoodleEvent
