package com.kglabs28.yapnplay.backend.domain.admin

/**
 * The admin players list's period and order: players who joined (or were
 * last seen, [by]) from [fromMillis] until [toMillis] (null: no bound),
 * listed by [sort], [ascending] or not.
 */
data class AdminUserFilter(
    val fromMillis: Long? = null,
    val toMillis: Long? = null,
    val by: AdminUserDate = AdminUserDate.JOINED,
    val sort: AdminUserSort = AdminUserSort.JOINED,
    /** Smallest / oldest / A first; otherwise biggest / newest / Z first. */
    val ascending: Boolean = false,
)

/** Which date the period looks at. */
enum class AdminUserDate { JOINED, LAST_SEEN }

/** The column the players list is ordered by (ties: newest first). */
enum class AdminUserSort { JOINED, NAME, COINS, XP, GAMES, TIME_IN_APP, LAST_SEEN }
