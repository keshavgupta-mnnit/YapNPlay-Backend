package com.kglabs28.yapnplay.backend.domain.match

/** One player's record in one room: matches finished there, matches won (1st), and coins earned (pot share + finishing). */
data class RoomTotals(val played: Int, val wins: Int, val coins: Int)
