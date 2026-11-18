package com.kglabs28.yapnplay.backend.games.doodleking

/**
 * Words for the drawing rounds. Keep words familiar, quick to sketch on a phone,
 * and easy to recognize. Difficulty comes from drawing complexity, not obscure
 * vocabulary. Hinglish words are included only when the room enables Hinglish.
 */
object DoodleKingWords {

    fun pool(level: DoodleLevel, hinglish: Boolean): List<String> =
        ENTRIES.filter { it.level == level && (hinglish || it.language == DoodleLanguage.ENGLISH) }
            .map { it.text }

    /** A word's category and language, or null for a word not in the list. */
    fun info(word: String): DoodleWordInfo? =
        BY_TEXT[word.lowercase()]?.let { DoodleWordInfo(it.category, it.language) }

    private class Entry(
        val text: String,
        val level: DoodleLevel,
        val language: DoodleLanguage,
        val category: DoodleCategory
    )

    private val ENTRIES: List<Entry> = buildEntries()
    private val BY_TEXT: Map<String, Entry> = ENTRIES.associateBy { it.text.lowercase() }

    private fun buildEntries(): List<Entry> {
        val list = mutableListOf<Entry>()

        fun add(level: DoodleLevel, language: DoodleLanguage, category: DoodleCategory, words: String) {
            words.split(",")
                .map { it.trim().lowercase() }
                .filter { it.isNotEmpty() && letters(it) <= MAX_LETTERS }
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

        // EASY: instantly recognizable, usually drawable with one or two shapes.
        add(easy, en, animals, "cat, dog, fish, bird, duck, frog, snake, pig, bee, bear, cow, sheep, mouse, ant, bat, chick, hen, goat, panda, zebra, puppy, rabbit")
        add(easy, en, food, "apple, banana, bread, cake, egg, pizza, cheese, candy, cookie, grapes, lemon, carrot, burger, milk, fries, juice, mango, pear, corn, pie, toast, soup, chips")
        add(easy, en, things, "ball, bed, bell, book, box, bus, car, chair, clock, cup, door, flag, fork, hat, key, kite, lamp, lock, mug, pen, phone, ring, shoe, sock, spoon, table, watch, candle, bottle, pencil, boat, bag, cap, tie, gift, bike, comb, mask, rope, drum, bulb, fan, mop, soap, tap, bowl, wallet, paper, brush, brush")
        add(easy, en, nature, "sun, moon, star, tree, leaf, rain, cloud, flower, fire, snow, hill, grass, rock")
        add(easy, en, places, "home, shop, park")
        add(easy, en, body, "eye, nose, hand, heart, ear, foot, lips, tooth, hair, toe, finger, smile")
        add(easy, en, fun_, "kite, party, selfie, yawn, laugh, dance, sleep")

        // MEDIUM: familiar words that need a few more parts or a clearer scene.
        add(medium, en, animals, "snail, crab, owl, lion, swan, shark, whale, horse, tiger, parrot, donkey, goose, beetle, monkey, turtle, camel, spider")
        add(medium, en, food, "donut, cherry, orange, tomato, waffle, taco, peanut, honey, pasta, sushi, nachos, muffin, hotdog, pickle, omelet, noodle, chips")
        add(medium, en, things, "anchor, magnet, hammer, tent, bucket, button, pillow, broom, teapot, saw, sword, arrow, axe, shield, taxi, sofa, plate, knife, glass, medal, boot, scarf, glove, dress, shirt, mirror, ruler, crayon, wheel, remote, zipper, parcel, tablet, tissue, ticket, ladder, crown, camera")
        add(medium, en, nature, "cactus, wave, comet, planet, storm, desert, river, forest, beach, palm, lava")
        add(medium, en, places, "school, garden, farm, zoo, jail, circus, hotel, market, bridge, temple")
        add(medium, en, body, "skull, mouth, brain, elbow, knee, muscle, tongue, beard")
        add(medium, en, fun_, "chess, darts, golf, tennis, zombie, mummy, ninja, disco, yoga, magic, karate, prank, pirate, clown, ghost")

        // HARD: still familiar; the challenge is a multi-part drawing or a scene,
        // never an unfamiliar animal or specialist word.
        add(hard, en, animals, "dragon")
        add(hard, en, food, "waffle, burger")
        add(hard, en, things, "fridge, stove, rocket, robot, ship, truck, plane, piano, guitar, cannon, laptop, helmet, trophy, radio, globe, flute, swing, slide, puzzle, engine, violin, statue, camera")
        add(hard, en, nature, "galaxy, meteor, crater")
        add(hard, en, places, "castle, tower, church, palace, prison, museum")
        add(hard, en, body, "ankle, navel")
        add(hard, en, fun_, "wizard, cowboy, alien, santa, angel, devil, knight, genie, cupid, joker, queen")

        // HINGLISH: common everyday words, using familiar Roman spellings.
        add(easy, hi, food, "chai, samosa, jalebi, laddu, lassi, aam, kela, roti, anda, gajar, seb, nimbu, puri, pakoda, momos")
        add(easy, hi, things, "patang, diya, joota, matka, topi, pankha, ghadi, kitab, chaabi, bindi, kurta, kursi, balti, jhaadu, lota, thali, kanghi")
        add(easy, hi, animals, "machli, billi, kutta, gaay, bandar, sher, chuha, murga")
        add(easy, hi, nature, "phool, suraj, chaand, taara, ped, baadal, barish, pahad, nadi")
        add(easy, hi, places, "ghar, dukaan, bazaar")
        add(easy, hi, body, "aankh, naak, kaan, haath, dil, pair")
        add(easy, hi, fun_, "naach, gaana")

        add(medium, hi, food, "kulfi, dosa, bhutta, idli, vada, paan, chaat, poha, halwa, barfi")
        add(medium, hi, things, "hookah, dabba, tiffin, jhoola, kadhai, tawa, belan, dhol, chulha, saari, lungi, chakki, tabla, sitar")
        add(medium, hi, animals, "hathi, oont, mor, gadha, bhalu, tota, bakri")
        add(medium, hi, nature, "aandhi, bijli")
        add(medium, hi, places, "mandir, masjid, dhaba, kuan")
        add(medium, hi, body, "ungli, daant")
        add(medium, hi, fun_, "garba, rakhi, holi, diwali, mela")

        add(hard, hi, food, "pakwan")
        add(hard, hi, things, "pagdi, auto")
        add(hard, hi, places, "mahal, ghat, gufa, qila")
        add(hard, hi, fun_, "lohri, onam")

        // Avoid accidental repeats within a language while preserving English/Hinglish
        // entries separately when the spelling is genuinely shared.
        return list.distinctBy { it.language to it.text }
    }

    /** Letters and digits in [text] (spaces and hyphens don't count). */
    private fun letters(text: String): Int = text.count { it.isLetterOrDigit() }
}

/** Maximum word length keeps prompts readable and drawable in a short round. */
const val MAX_LETTERS = 6

/** Short, familiar hints shown to guessers. */
enum class DoodleCategory(val label: String) {
    ANIMALS("Animals"),
    FOOD("Food & Drinks"),
    OBJECTS("Everyday Things"),
    NATURE("Nature"),
    PLACES("Places"),
    BODY("Body & Actions"),
    FUN("Fun & Games")
}

enum class DoodleLanguage(val label: String) { ENGLISH("English"), HINGLISH("Hinglish") }

data class DoodleWordInfo(val category: DoodleCategory, val language: DoodleLanguage)

/** Round 1 = easy, round 2 = medium, round 3 and later = hard. */
enum class DoodleLevel {
    EASY, MEDIUM, HARD;

    companion object {
        fun ofRound(round: Int): DoodleLevel =
            entries[(round - 1).coerceIn(0, entries.size - 1)]
    }
}
