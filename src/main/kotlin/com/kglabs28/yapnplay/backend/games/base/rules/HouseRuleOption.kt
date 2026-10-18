package com.kglabs28.yapnplay.backend.games.base.rules

/**
 * One toggle a room leader may flip before a match ("Extra turn on a 6").
 * [key] is the wire key in `RoomInfo.houseRules`; the game maps the
 * on/off map back onto its own HouseRules when the match starts. Public
 * (random) matches always use [defaultEnabled].
 */
data class HouseRuleOption(
    val key: String,
    val title: String,
    val description: String,
    val defaultEnabled: Boolean,
)
