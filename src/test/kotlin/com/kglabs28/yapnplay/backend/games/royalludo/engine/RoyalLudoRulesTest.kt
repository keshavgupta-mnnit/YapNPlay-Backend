package com.kglabs28.yapnplay.backend.games.royalludo.engine

import com.kglabs28.yapnplay.backend.games.base.rules.PlayMode
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RoyalLudoRulesTest {

    @Test
    fun `traditional rules allowed in private room`() {
        assertTrue(RoyalLudoRules.TRADITIONAL.isAllowed(PlayMode.PRIVATE_ROOM))
    }

    @Test
    fun `traditional rules allowed in public match`() {
        assertTrue(RoyalLudoRules.TRADITIONAL.isAllowed(PlayMode.PUBLIC_MATCH))
    }

    @Test
    fun `custom house rules allowed in private room`() {
        val custom = RoyalLudoRules.TRADITIONAL.copy(
            house = RoyalLudoRules.TRADITIONAL.house.copy(baseExitRolls = setOf(1, 6))
        )
        assertTrue(custom.isAllowed(PlayMode.PRIVATE_ROOM))
    }

    @Test
    fun `custom house rules rejected in public match`() {
        val custom = RoyalLudoRules.TRADITIONAL.copy(
            house = RoyalLudoRules.TRADITIONAL.house.copy(baseExitRolls = setOf(1, 6))
        )
        assertFalse(custom.isAllowed(PlayMode.PUBLIC_MATCH))
    }

    @Test
    fun `custom mandatory rules rejected in public match`() {
        val custom = RoyalLudoRules.TRADITIONAL.copy(
            mandatory = RoyalLudoRules.TRADITIONAL.mandatory.copy(exactFinish = false)
        )
        assertFalse(custom.isAllowed(PlayMode.PUBLIC_MATCH))
    }
}
