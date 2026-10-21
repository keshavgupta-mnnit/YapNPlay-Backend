package com.kglabs28.yapnplay.backend.application

import com.kglabs28.yapnplay.backend.application.match.games.citybazaar.CityBazaarSessionFactory
import com.kglabs28.yapnplay.backend.domain.common.Clock
import com.kglabs28.yapnplay.backend.games.base.play.GameRandom
import com.kglabs28.yapnplay.backend.games.citybazaar.engine.CityBazaarEngine
import com.kglabs28.yapnplay.backend.games.citybazaar.utils.CITY_BAZAAR_HOUSE_RULES
import com.kglabs28.yapnplay.backend.support.testJson
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Money popups are a room house rule: rooms may switch them off, Quick Play always shows them. */
class CityBazaarMoneyPopupsTest {
    private val factory = CityBazaarSessionFactory(CityBazaarEngine(), object : GameRandom {
        override fun nextInt(bound: Int) = 0
    }, testJson, Clock { 0L })

    private fun moneyPopups(houseRules: Map<String, Boolean>?): Boolean =
        factory.start(3, houseRules).publicState(null)["moneyPopups"]!!.jsonPrimitive.boolean

    @Test
    fun `quick play shows popups, a room can switch them off`() {
        assertTrue(CITY_BAZAAR_HOUSE_RULES.any { it.key == "money_popups" && it.defaultEnabled })
        assertEquals(true, moneyPopups(null))
        assertEquals(true, moneyPopups(emptyMap()))
        assertEquals(false, moneyPopups(mapOf("money_popups" to false)))
    }
}
