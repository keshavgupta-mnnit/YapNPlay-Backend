package com.kglabs28.yapnplay.backend.infrastructure.security

import com.kglabs28.yapnplay.backend.games.base.play.GameRandom
import java.security.SecureRandom

/**
 * Dice and shuffles for real matches: the operating system's secure
 * source, uniform and independent every time (contract "Fairness").
 */
class SecureGameRandom(private val random: SecureRandom = SecureRandom()) : GameRandom {
    override fun nextInt(bound: Int): Int = random.nextInt(bound)
}
