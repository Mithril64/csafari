package dev.xispana.safari

enum class Biome(val display: String) {
    CAVERN("Cavern"),
    FOREST("Forest"),
    HAUNTED("Haunted"),
    ICY("Icy")
}

enum class Rarity(val short: String) {
    COMMON("C"), UNCOMMON("U"), RARE("R"), EPIC("E"), LEGENDARY("L")
}

data class Critter(val name: String, val biome: Biome, val rarity: Rarity)

/**
 * All 37 critters native to the Critter Safari.
 * Haunted has 10, every other biome has 9.
 */
object Critters {

    val ALL: List<Critter> = listOf(
        // --- Cavern (9) ---
        Critter("Cavernfish", Biome.CAVERN, Rarity.COMMON),
        Critter("Flitter", Biome.CAVERN, Rarity.COMMON),
        Critter("Shyworm", Biome.CAVERN, Rarity.COMMON),
        Critter("Driftling", Biome.CAVERN, Rarity.UNCOMMON),
        Critter("Chuckwalla", Biome.CAVERN, Rarity.RARE),
        Critter("Rockmite", Biome.CAVERN, Rarity.RARE),
        Critter("Scrappy", Biome.CAVERN, Rarity.RARE),
        Critter("Snoozle", Biome.CAVERN, Rarity.RARE),
        Critter("Gemzie", Biome.CAVERN, Rarity.EPIC),

        // --- Forest (9) ---
        Critter("Foxtrot", Biome.FOREST, Rarity.COMMON),
        Critter("Bluebird", Biome.FOREST, Rarity.UNCOMMON),
        Critter("Honeybug", Biome.FOREST, Rarity.UNCOMMON),
        Critter("Treefrog", Biome.FOREST, Rarity.UNCOMMON),
        Critter("Woodchucker", Biome.FOREST, Rarity.UNCOMMON),
        Critter("Fluffling", Biome.FOREST, Rarity.RARE),
        Critter("Hideonfloor", Biome.FOREST, Rarity.RARE),
        Critter("Parakeet", Biome.FOREST, Rarity.RARE),
        Critter("Macaw", Biome.FOREST, Rarity.LEGENDARY),

        // --- Haunted (10) ---
        Critter("Areita", Biome.HAUNTED, Rarity.UNCOMMON),
        Critter("Bloodbat", Biome.HAUNTED, Rarity.UNCOMMON),
        Critter("Duplico", Biome.HAUNTED, Rarity.UNCOMMON),
        Critter("Gazer", Biome.HAUNTED, Rarity.UNCOMMON),
        Critter("Litterbug", Biome.HAUNTED, Rarity.UNCOMMON),
        Critter("Solsnatcher", Biome.HAUNTED, Rarity.UNCOMMON),
        Critter("Gimmiegold", Biome.HAUNTED, Rarity.RARE),
        Critter("Hideonwall", Biome.HAUNTED, Rarity.RARE),
        Critter("Hideyho", Biome.HAUNTED, Rarity.RARE),
        Critter("Doomspiral", Biome.HAUNTED, Rarity.LEGENDARY),

        // --- Icy (9) ---
        Critter("Strongarm", Biome.ICY, Rarity.COMMON),
        Critter("Tepid", Biome.ICY, Rarity.COMMON),
        Critter("Polaris", Biome.ICY, Rarity.UNCOMMON),
        Critter("Shuddersquid", Biome.ICY, Rarity.UNCOMMON),
        Critter("Billygoat", Biome.ICY, Rarity.RARE),
        Critter("Mantis Shrimp", Biome.ICY, Rarity.RARE),
        Critter("Nozzlenose", Biome.ICY, Rarity.RARE),
        Critter("Troodon", Biome.ICY, Rarity.RARE),
        Critter("Wumpa", Biome.ICY, Rarity.LEGENDARY)
    )

    val TOTAL: Int = ALL.size // 37

    private val BY_NAME: Map<String, Critter> = ALL.associateBy { it.name.lowercase() }

    val BY_BIOME: Map<Biome, List<Critter>> = ALL.groupBy { it.biome }

    /**
     * Critters that don't count toward a biome being "complete" - just Macaw for now.
     * Legendary-rarity critters are rare enough that requiring one to call a biome done
     * isn't a practical goal; Macaw is still fully tracked everywhere else (it still counts
     * toward the overall dex total and still gets its own "New:" line if you do catch it).
     */
    val OPTIONAL_FOR_COMPLETION: Set<Critter> = setOf(ALL.first { it.name == "Macaw" })

    /** Returns the critter if [name] is a real critter, else null (so usernames never match). */
    fun resolve(name: String): Critter? = BY_NAME[name.trim().lowercase()]

    // Longest name first, so a name that's a prefix of another (none currently, but future
    // critters might be) can't shadow the more specific match.
    private val BY_LENGTH_DESC: List<Critter> = ALL.sortedByDescending { it.name.length }

    /**
     * Returns the critter whose name [text] starts with, ignoring whatever comes after it.
     * Used where a chat line's tail is unpredictable (Hideyho, for one, has its own capture
     * dialogue instead of the generic "and gained/received a ... Shard!" every other critter
     * uses), so matching is anchored on the name itself rather than a fixed sentence ending.
     */
    fun matchAtStart(text: String): Critter? = BY_LENGTH_DESC.firstOrNull { text.startsWith(it.name) }
}
