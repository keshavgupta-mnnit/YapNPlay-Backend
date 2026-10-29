package com.kglabs28.yapnplay.backend.domain.admin

/**
 * The admin players list's period and order: players who joined (or were
 * last seen, [by]) from [fromMillis] until [toMillis] (null: no bound),
 * listed by [sort].
 */
data class AdminUserFilter(
    val fromMillis: Long? = null,
    val toMillis: Long? = null,
    val by: AdminUserDate = AdminUserDate.JOINED,
    val sort: AdminUserSort = AdminUserSort.NEWEST,
)

/** Which date the period looks at. */
enum class AdminUserDate { JOINED, LAST_SEEN }

/** The players list's order, biggest first (oldest for [OLDEST]). */
enum class AdminUserSort { NEWEST, OLDEST, COINS, XP, TIME_IN_APP, LAST_SEEN }
