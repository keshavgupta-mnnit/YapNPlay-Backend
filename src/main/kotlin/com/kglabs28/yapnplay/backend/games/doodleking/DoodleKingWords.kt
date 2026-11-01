package com.kglabs28.yapnplay.backend.games.doodleking

/** The words to draw, by level; Hinglish ones join only when a room turns them on. */
object DoodleKingWords {

    fun pool(level: DoodleLevel, hinglish: Boolean): List<String> = when (level) {
        DoodleLevel.EASY -> EASY + if (hinglish) HINGLISH_EASY else emptyList()
        DoodleLevel.MEDIUM -> MEDIUM + if (hinglish) HINGLISH_MEDIUM else emptyList()
        DoodleLevel.HARD -> HARD + if (hinglish) HINGLISH_HARD else emptyList()
    }

    private val EASY = listOf(
        "apple", "banana", "ball", "bat", "bed", "bell", "bird", "boat", "book", "bottle", "box", "bread", "bridge", "brush",
        "bus", "cake", "camera", "candle", "car", "cat", "chair", "clock", "cloud", "comb", "cow", "crown", "cup", "dog",
        "door", "drum", "duck", "egg", "eye", "fan", "fish", "flag", "flower", "fork", "frog", "glasses", "guitar", "hat",
        "heart", "horse", "house", "ice cream", "key", "kite", "ladder", "lamp", "leaf", "lion", "lock", "moon", "mouse",
        "mug", "nose", "pen", "pencil", "phone", "pizza", "rain", "ring", "rocket", "shoe", "snake", "sock", "spoon", "star",
        "sun", "table", "tree", "train", "tv", "umbrella", "watch", "window", "fire", "hand", "mango",
    )

    private val MEDIUM = listOf(
        "airplane", "anchor", "backpack", "balloon", "bicycle", "binoculars", "butterfly", "cactus", "castle", "chimney",
        "cricket bat", "dinosaur", "dolphin", "dragon", "elephant", "envelope", "feather", "giraffe", "hammer", "headphones",
        "helicopter", "igloo", "jellyfish", "kangaroo", "keyboard", "lighthouse", "lollipop", "magnet", "map", "mermaid",
        "microphone", "mountain", "mushroom", "octopus", "owl", "parachute", "peacock", "penguin", "piano", "pineapple",
        "pirate", "pyramid", "rainbow", "robot", "sandwich", "scissors", "shark", "skateboard", "snowman", "spider web",
        "submarine", "sunflower", "swing", "telescope", "tent", "tiger", "toothbrush", "tractor", "treasure", "turtle",
        "volcano", "waterfall", "wheelchair", "windmill", "zebra", "camel", "candy", "crab", "football", "hot air balloon",
    )

    private val HARD = listOf(
        "alarm clock", "astronaut", "birthday party", "black hole", "bowling", "campfire", "car wash", "chess", "circus",
        "cockroach", "cowboy", "crossword", "earthquake", "eclipse", "electricity", "escalator",
        "family photo", "ferris wheel", "fireworks", "fishing", "gravity", "haircut", "hide and seek", "homework",
        "jet lag", "karate", "lightning", "magician", "marathon", "midnight", "nightmare", "orchestra", "password",
        "photographer", "picnic", "police station", "power cut", "quicksand", "recycle", "roller coaster", "sandcastle",
        "scarecrow", "selfie", "shadow", "sleepwalking", "snowball fight", "solar system", "spaceship", "superhero",
        "surfing", "thunderstorm", "time machine", "traffic jam", "treasure hunt", "tug of war", "vacation", "wedding",
        "wifi", "zombie", "traffic light", "rush hour", "yoga", "zoo",
    )

    private val HINGLISH_EASY = listOf(
        "chai", "samosa", "patang", "diya", "chappal", "jalebi", "ladoo", "lassi", "aam", "kela", "matka", "topi",
        "chashma", "pankha", "ghadi", "joota", "kitaab", "ghar", "phool", "machhli", "billi", "kutta", "haathi", "suraj",
        "chaand", "taara", "ped", "chaabi", "bindi", "kurta",
    )

    private val HINGLISH_MEDIUM = listOf(
        "rangoli", "auto rickshaw", "tiranga", "mandir", "dhol", "tabla", "chulha", "bhutta", "nariyal", "pani puri",
        "gol gappa", "tiffin", "kulfi", "dosa", "chakla belan", "jhoola", "lattoo", "gilli danda", "mehndi", "tanga",
        "dupatta", "hukka", "dabba", "chowkidar", "mor",
    )

    private val HINGLISH_HARD = listOf(
        "kabaddi", "kho kho", "antakshari", "baraat", "dahi handi", "holi", "diwali", "kumbh mela", "rath yatra",
        "chandrayaan", "jugaad", "bollywood", "chai pe charcha", "nukkad natak", "shaadi", "doodhwala", "bhel puri",
        "sabzi mandi", "kaanwar", "garba",
    )
}

/** The round's word level: round 1 easy, 2 medium, 3 and later hard. */
enum class DoodleLevel { EASY, MEDIUM, HARD;

    companion object {
        fun ofRound(round: Int): DoodleLevel = entries[(round - 1).coerceIn(0, entries.size - 1)]
    }
}
