package com.kglabs28.yapnplay.backend.application.moderation

import java.util.concurrent.ConcurrentHashMap
import com.kglabs28.yapnplay.backend.application.common.ServiceException
import com.kglabs28.yapnplay.backend.application.match.MatchRegistry
import com.kglabs28.yapnplay.backend.application.realtime.ClientNotifier
import com.kglabs28.yapnplay.backend.application.realtime.EnvelopeCodec
import com.kglabs28.yapnplay.backend.application.rooms.RoomPresence
import com.kglabs28.yapnplay.backend.config.BackendConstants
import com.kglabs28.yapnplay.backend.config.BackendStrings
import com.kglabs28.yapnplay.backend.contract.ErrorCode
import com.kglabs28.yapnplay.backend.contract.PenaltyIssued
import com.kglabs28.yapnplay.backend.contract.ReportPlayer
import com.kglabs28.yapnplay.backend.contract.ReportResolved
import com.kglabs28.yapnplay.backend.contract.ReportVoteRequested
import com.kglabs28.yapnplay.backend.contract.VoteResponse
import com.kglabs28.yapnplay.backend.contract.transport.Channel
import com.kglabs28.yapnplay.backend.contract.transport.MessageType
import com.kglabs28.yapnplay.backend.domain.common.Clock
import com.kglabs28.yapnplay.backend.domain.common.IdGenerator
import com.kglabs28.yapnplay.backend.domain.identity.UserRepository
import com.kglabs28.yapnplay.backend.domain.moderation.Ballot
import com.kglabs28.yapnplay.backend.domain.moderation.ModerationRepository
import com.kglabs28.yapnplay.backend.domain.moderation.Penalty
import com.kglabs28.yapnplay.backend.domain.moderation.ReportRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Voice-chat reports decided by the people present (contract "Moderation
 * channel"). Voters are everyone at the table or in the room except the
 * two people involved; 2 agreements with 4 or fewer voters, otherwise 3.
 * Every voter gets a stored ballot. The loser of the vote gets an
 * incident: points taken and new matches refused for a while.
 */
class ModerationService(
    private val repository: ModerationRepository,
    private val users: UserRepository,
    private val matches: MatchRegistry,
    private val presence: RoomPresence,
    private val notifier: ClientNotifier,
    private val codec: EnvelopeCodec,
    private val ids: IdGenerator,
    private val clock: Clock,
    private val scope: CoroutineScope,
) {
    private val lock = Mutex()
    private val pending = mutableMapOf<String, PendingReport>()

    suspend fun report(reporterId: String, request: ReportPlayer) {
        val present = matches.find(request.contextId)?.presentUsers ?: presence.usersIn(request.contextId)
        val voters = present - reporterId - request.reportedUserId
        if (reporterId !in present || voters.size < BackendConstants.REPORT_MIN_VOTERS) {
            throw ServiceException(ErrorCode.BAD_REQUEST, BackendStrings.CANNOT_REPORT)
        }
        val now = clock.nowMillis()
        val required = if (voters.size <= BackendConstants.REPORT_SMALL_GROUP) {
            BackendConstants.REPORT_SMALL_GROUP_AGREEMENTS
        } else {
            BackendConstants.REPORT_AGREEMENTS
        }
        val reporterName = users.findById(reporterId)?.displayName.orEmpty()
        val reportedName = users.findById(request.reportedUserId)?.displayName.orEmpty()
        val report = PendingReport(ids.newId(), reporterId, request.reportedUserId, reportedName, voters, required, now + BackendConstants.REPORT_VOTE_MS)
        repository.insertReport(ReportRecord(report.id, request.contextId, reporterId, request.reportedUserId, request.reason, required, now))
        lock.withLock { pending[report.id] = report }
        val ask = codec.push(
            Channel.MODERATION,
            MessageType.REPORT_VOTE_REQUESTED,
            ReportVoteRequested.serializer(),
            ReportVoteRequested(report.id, reporterName, reportedName, report.expiresAt),
        )
        voters.forEach { notifier.send(it, ask) }
        scope.launch {
            delay(BackendConstants.REPORT_VOTE_MS)
            resolve(report.id)
        }
    }

    suspend fun vote(voterId: String, reportId: String, agree: Boolean) {
        val complete = lock.withLock {
            val report = pending[reportId] ?: throw ServiceException(ErrorCode.NOT_FOUND, BackendStrings.NO_SUCH_REPORT)
            if (voterId !in report.voters) throw ServiceException(ErrorCode.NOT_MEMBER, BackendStrings.NO_SUCH_REPORT)
            report.answers.putIfAbsent(voterId, agree to clock.nowMillis())
            report.answers.size == report.voters.size
        }
        if (complete) resolve(reportId)
    }

    /** When each player last lifted a block with an ad (kept in memory; resets on restart). */
    private val lastAdClear = ConcurrentHashMap<String, Long>()

    /**
     * A rewarded ad was watched: the block is lifted. Ads are not verified
     * yet, so this works at most once a day per player.
     */
    suspend fun clearWithAd(userId: String) {
        val now = clock.nowMillis()
        val last = lastAdClear[userId]
        if (last != null && now - last < BackendConstants.AD_CLEAR_COOLDOWN_MS) {
            throw ServiceException(ErrorCode.RATE_LIMITED, BackendStrings.AD_CLEAR_LIMIT)
        }
        lastAdClear[userId] = now
        repository.clearBlocks(userId, now)
    }

    /** On connecting: remind the user of a block still running. */
    suspend fun sendActiveBlock(userId: String) {
        val block = repository.activeBlock(userId, clock.nowMillis()) ?: return
        notifier.send(userId, penaltyEnvelope(PenaltyIssued(block.points, block.blockedUntil)))
    }

    private suspend fun resolve(reportId: String) {
        val report = lock.withLock { pending.remove(reportId) } ?: return
        val now = clock.nowMillis()
        val ballots = report.voters.map { voter ->
            val answer = report.answers[voter]
            val response = when (answer?.first) {
                true -> VoteResponse.AGREE
                false -> VoteResponse.DISAGREE
                null -> VoteResponse.NO_RESPONSE
            }
            Ballot(report.id, voter, response.name, answer?.second ?: now)
        }
        val agreed = ballots.count { it.response == VoteResponse.AGREE.name }
        val disagreed = ballots.count { it.response == VoteResponse.DISAGREE.name }
        val upheld = agreed >= report.required
        repository.resolve(report.id, upheld, ballots, now)
        val resolved = ReportResolved(report.id, upheld, report.reportedName, agreed, disagreed, ballots.size - agreed - disagreed, report.required)
        notifier.send(report.reporterId, codec.push(Channel.MODERATION, MessageType.REPORT_RESOLVED, ReportResolved.serializer(), resolved))
        penalise(if (upheld) report.reportedId else report.reporterId, report.id, now)
    }

    private suspend fun penalise(userId: String, reportId: String, now: Long) {
        val penalty = Penalty(ids.newId(), userId, reportId, BackendConstants.PENALTY_POINTS, now + BackendConstants.PENALTY_BLOCK_MS, now)
        repository.insertPenalty(penalty)
        users.addXp(userId, -penalty.points)
        notifier.send(userId, penaltyEnvelope(PenaltyIssued(penalty.points, penalty.blockedUntil)))
    }

    private fun penaltyEnvelope(penalty: PenaltyIssued) =
        codec.push(Channel.MODERATION, MessageType.PENALTY_ISSUED, PenaltyIssued.serializer(), penalty)
}
