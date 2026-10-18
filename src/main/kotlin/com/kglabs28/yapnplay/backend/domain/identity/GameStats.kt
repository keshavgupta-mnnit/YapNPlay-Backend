package com.kglabs28.yapnplay.backend.domain.identity

/** Server-counted totals over all matches (Quick Play and rooms). */
data class GameStats(
    val played: Int,
    val won: Int,
)
