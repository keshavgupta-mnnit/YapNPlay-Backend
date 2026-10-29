package com.kglabs28.yapnplay.backend.application.admin

/** The dashboard's period: from [fromMillis] (included) to [toMillis] (left out). [ALL] is every time. */
data class StatsRange(val fromMillis: Long, val toMillis: Long) {
    val isAll: Boolean get() = this == ALL

    companion object {
        val ALL = StatsRange(0L, Long.MAX_VALUE)

        /** From the page's `from` / `to` (epoch millis); neither given means every time. */
        fun of(from: Long?, to: Long?): StatsRange =
            if (from == null && to == null) ALL else StatsRange(from ?: 0L, to ?: Long.MAX_VALUE)
    }
}
