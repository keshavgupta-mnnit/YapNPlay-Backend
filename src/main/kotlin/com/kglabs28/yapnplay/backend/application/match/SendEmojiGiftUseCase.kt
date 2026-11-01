package com.kglabs28.yapnplay.backend.application.match

import com.kglabs28.yapnplay.backend.application.common.ServiceException
import com.kglabs28.yapnplay.backend.application.economy.CoinsService
import com.kglabs28.yapnplay.backend.application.match.actor.MatchAction
import com.kglabs28.yapnplay.backend.contract.EmojiGiftResult
import com.kglabs28.yapnplay.backend.contract.SendEmojiGift

/**
 * The user sends an emoji to another seat at their table: its price is taken
 * first (NOT_ENOUGH_COINS: nothing happens), then the match shows it to the
 * whole table. If the match refuses it (not seated, a wrong seat, the match
 * over), the coins go back and the refusal is passed on.
 */
class SendEmojiGiftUseCase(
    private val coins: CoinsService,
    private val send: SendMatchActionUseCase,
) {
    suspend operator fun invoke(userId: String, request: SendEmojiGift): EmojiGiftResult {
        val price = coins.emojiGiftPrice(request.emojiId)
        val balance = coins.payForEmojiGift(userId, request.matchId, price)
        try {
            send(userId, request.matchId, MatchAction.SendEmoji(request.emojiId, request.toSeat))
        } catch (e: ServiceException) {
            coins.refundEmojiGift(userId, request.matchId, price)
            throw e
        }
        return EmojiGiftResult(coins = balance, spent = price)
    }
}
