package com.kglabs28.yapnplay.backend.api.ws

import com.kglabs28.yapnplay.backend.config.BackendConstants
import com.kglabs28.yapnplay.backend.domain.common.Clock

/** At most [perSecond] messages in any one-second window, per connection. */
class MessageRateLimiter(
    private val clock: Clock,
    private val perSecond: Int = BackendConstants.WS_MESSAGES_PER_SECOND,
) {
    private val recent = ArrayDeque<Long>()

    /** Records a message; false when it is over the limit. */
    fun tryAcquire(): Boolean {
        val now = clock.nowMillis()
        while (recent.isNotEmpty() && now - recent.first() >= BackendConstants.MILLIS_PER_SECOND) recent.removeFirst()
        if (recent.size >= perSecond) return false
        recent.addLast(now)
        return true
    }
}
