package com.kglabs28.yapnplay.backend.application.stats

import com.kglabs28.yapnplay.backend.contract.OnlineStats
import com.kglabs28.yapnplay.backend.application.realtime.ClientNotifier

/** `GET /stats/online`: users connected right now (the Game Zone pill). */
class GetOnlineStatsUseCase(private val notifier: ClientNotifier) {
    operator fun invoke(): OnlineStats = OnlineStats(online = notifier.onlineUserCount())
}
