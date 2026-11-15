package com.kglabs28.yapnplay.backend.games.doodleking

/**
 * The words to draw: each has a level (the round it comes up in), a simple
 * category and a language, so the guessers can be told "Food · Hinglish".
 * Every word is at most [MAX_LETTERS] letters (spaces don't count), so it can
 * be drawn and guessed in the time. Hinglish words join only when a room turns them on.
 */
object DoodleKingWords {

    fun pool(level: DoodleLevel, hinglish: Boolean): List<String> =
        ENTRIES.filter { it.level == level && (hinglish || it.language == DoodleLanguage.ENGLISH) }.map { it.text }

    /** A word's category and language, or null for a word not in the list. */
    fun info(word: String): DoodleWordInfo? = BY_TEXT[word]?.let { DoodleWordInfo(it.category, it.language) }

    private class Entry(val text: String, val level: DoodleLevel, val language: DoodleLanguage, val category: DoodleCategory)

    private val ENTRIES: List<Entry> = buildEntries()

    private val BY_TEXT: Map<String, Entry> = ENTRIES.associateBy { it.text }

    private fun buildEntries(): List<Entry> {
        val list = mutableListOf<Entry>()
        fun add(level: DoodleLevel, language: DoodleLanguage, category: DoodleCategory, words: String) {
            words.split(",").map { it.trim() }.filter { it.isNotEmpty() && letters(it) <= MAX_LETTERS }
                .forEach { list += Entry(it, level, language, category) }
        }
        val en = DoodleLanguage.ENGLISH
        val hi = DoodleLanguage.HINGLISH
        val easy = DoodleLevel.EASY
        val medium = DoodleLevel.MEDIUM
        val hard = DoodleLevel.HARD
        val animals = DoodleCategory.ANIMALS
        val food = DoodleCategory.FOOD
        val things = DoodleCategory.OBJECTS
        val nature = DoodleCategory.NATURE
        val places = DoodleCategory.PLACES
        val body = DoodleCategory.BODY
        val fun_ = DoodleCategory.FUN

        // Only what can be sketched with a finger on a phone in about a minute: simple shapes or an
        // icon everyone knows. Easy = a shape or two; medium = a few parts; hard = many parts or a character.

        // ── English, easy ──
        add(easy, en, animals, "cat, dog, fish, bird, duck, frog, snake, pig, bee, bear, cow, sheep, mouse")
        add(easy, en, food, "apple, banana, bread, cake, egg, pizza, cheese, candy, cookie, grapes, lemon, carrot, burger, milk")
        add(easy, en, things, "ball, bed, bell, book, box, bus, car, chair, clock, cup, door, flag, fork, hat, key, kite, lamp, lock, mug, pen, phone, ring, shoe, sock, spoon, table, watch, candle, bottle, window, pencil, ladder, crown, boat, bag, cap, tie, gift, bike")
        add(easy, en, nature, "sun, moon, star, tree, leaf, rain, cloud, flower, fire, snow")
        add(easy, en, body, "eye, nose, hand, heart, ear, foot")

        // ── English, medium ──
        add(medium, en, animals, "snail, crab, owl, rabbit, lion, swan, shark, whale")
        add(medium, en, food, "donut, cherry, orange, tomato, waffle, taco, peanut, honey")
        add(medium, en, things, "anchor, magnet, hammer, tent, bucket, button, pillow, broom, teapot, saw, sword, arrow, axe, shield, taxi, sofa, plate, knife, glass, medal, boot, scarf, glove, dress, shirt")
        add(medium, en, places, "igloo, cave, island")
        add(medium, en, nature, "cactus, wave, comet, planet, storm")
        add(medium, en, body, "skull, mouth")
        add(medium, en, fun_, "chess, darts, golf, tennis")

        // ── English, hard ──
        add(hard, en, animals, "spider, turtle, camel, dragon")
        add(hard, en, things, "fridge, stove, bridge, rocket, robot, ship, truck, plane, piano, guitar, camera, cannon, laptop, helmet, trophy, radio, globe, flute, swing, slide, puzzle")
        add(hard, en, places, "castle, tower, church, temple")
        add(hard, en, fun_, "pirate, wizard, cowboy, alien, clown, santa, angel, devil, ghost, wifi")

        // ── Hinglish (the common spellings), easy ──
        add(easy, hi, food, "chai, samosa, jalebi, laddu, lassi, aam, kela, roti")
        add(easy, hi, things, "patang, diya, joota, matka, topi, pankha, ghadi, kitab, chabi, bindi, kurta")
        add(easy, hi, animals, "machli, billi, kutta")
        add(easy, hi, nature, "phool, suraj, chand, tara, ped")
        add(easy, hi, places, "ghar")

        // ── Hinglish, medium ──
        add(medium, hi, food, "kulfi, dosa, bhutta, idli, vada, paan")
        add(medium, hi, things, "hookah, dabba, tiffin, jhoola, kadhai, tawa, belan, dhol")
        add(medium, hi, animals, "hathi")
        add(medium, hi, places, "mandir")

        // ── Hinglish, hard ──
        add(hard, hi, fun_, "holi, diwali, mela")
        add(hard, hi, things, "pagdi, auto")
        add(hard, hi, places, "masjid")

        // ── More fun, quick-to-draw words (2026-11-15): fewer repeats, same short lengths ──
        add(easy, en, animals, "ant, bat, worm, chick, hen, panda, zebra, goat, puppy")
        add(easy, en, food, "fries, juice, mango, pear, melon, corn, pie, toast, soup, noodle, lolly, chips")
        add(easy, en, things, "comb, mask, toilet, nail, rope, drum, bulb, fan, mop, soap, tap, bowl, wallet, tv, paper")
        add(easy, en, nature, "hill, grass, rock, bubble, puddle")
        add(easy, en, body, "lips, tooth, hair, smile, toe, beard, finger")
        add(easy, en, fun_, "selfie, yawn, sneeze, snore, party, poop, smiley")
        add(medium, en, animals, "horse, tiger, parrot, donkey, rhino, hippo, koala, squid, goose, beetle")
        add(medium, en, food, "pasta, sushi, nachos, muffin, hotdog, pickle, omelet")
        add(medium, en, things, "mirror, ruler, crayon, wheel, remote, zipper, parcel, tablet, tissue, ticket")
        add(medium, en, nature, "desert, river, forest, beach, palm, lava")
        add(medium, en, places, "school, garden, farm, zoo, park, jail, circus")
        add(medium, en, body, "brain, elbow, knee, muscle, tongue")
        add(medium, en, fun_, "zombie, mummy, ninja, disco, yoga, magic, karate, prank")
        add(hard, en, animals, "walrus, beaver, toucan, llama, sloth")
        add(hard, en, things, "drone, crane, engine, violin, banjo, statue")
        add(hard, en, places, "palace, prison, hotel, office, market, museum")
        add(hard, en, nature, "galaxy, meteor")
        add(hard, en, fun_, "knight, genie, yeti, cupid, joker, queen, hacker")
        add(easy, hi, food, "anda, gajar, seb, nimbu, puri, pakoda, momos")
        add(easy, hi, things, "kursi, balti, jhadu, lota, thali, kanghi")
        add(easy, hi, animals, "gaay, bandar, sher, chuha, murga, oont")
        add(easy, hi, nature, "baadal, barish, pahad, nadi")
        add(easy, hi, body, "aankh, naak, kaan, haath, dil")
        add(medium, hi, food, "chaat, poha, halwa, barfi")
        add(medium, hi, things, "chulha, saree, lungi, chakki, tabla, sitar")
        add(medium, hi, animals, "mor, gadha, bhalu, tota")
        add(medium, hi, places, "dhaba, bazaar, kuan")
        add(medium, hi, fun_, "garba, rakhi")
        add(hard, hi, fun_, "lohri, onam")
        add(hard, hi, places, "mahal, ghat, gufa")
        return list
    }

    /** Letters and digits in [text] (spaces and hyphens don't count). */
    private fun letters(text: String): Int = text.count { it.isLetterOrDigit() }
}

/** The longest word, in letters: longer ones are hard to draw and guess in the time. */
const val MAX_LETTERS = 6

/** What a word is, in a few simple groups (shown to the guessers). */
enum class DoodleCategory(val label: String) {
    ANIMALS("Animals"), FOOD("Food"), OBJECTS("Objects"), NATURE("Nature"), PLACES("Places"), BODY("Body"), FUN("Fun"),
}

/** The language a word is in (shown to the guessers). */
enum class DoodleLanguage(val label: String) { ENGLISH("English"), HINGLISH("Hinglish") }

/** A word's category and language. */
data class DoodleWordInfo(val category: DoodleCategory, val language: DoodleLanguage)

/** The round's word level: round 1 easy, 2 medium, 3 and later hard. */
enum class DoodleLevel { EASY, MEDIUM, HARD;

    companion object {
        fun ofRound(round: Int): DoodleLevel = entries[(round - 1).coerceIn(0, entries.size - 1)]
    }
}
