package com.kglabs28.yapnplay.backend.application.match.games

/** How the server plays a seat. */
enum class BotMode {
    /** CPU seats, away or bot-held seats: the full bot. */
    FULL,

    /** The player switched Autoplay on: the bot plays their whole turn for them (City Bazaar: a bolder profile). */
    AUTOPLAY,

    /** The owner let their turn time run out: the cautious bot (City Bazaar adds the late-roll fine). */
    TIMEOUT,
}
