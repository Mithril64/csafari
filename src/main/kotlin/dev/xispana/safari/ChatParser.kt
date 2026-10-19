package dev.xispana.safari

/**
 * Parses Critter Safari chat lines.
 *
 * Only the fixed prefix of each line type is matched by regex ("CAPTURE! You caught a ",
 * "... catching a ", "... caught a SPARKLING "). The critter name itself is matched against
 * the known 37-critter list directly (Critters.matchAtStart), not captured as "whatever text
 * comes before the word 'and'". That matters because at least one critter (Hideyho) has its
 * own capture dialogue instead of the generic "and gained/received a ... Shard!" every other
 * critter uses - anchoring on the name instead of the sentence ending means a critter with
 * unique flavour text still gets detected correctly.
 *
 * Usernames are never matched as critters for two reasons:
 *  1. the critter is only ever looked for right after a fixed grammatical slot
 *     ("You caught a X", "... catching a X"), never from the whole line;
 *  2. the text found there is validated against the 37 known critters.
 *
 * So "LOOT SHARE! You received 2x Billygoat Shard from Xispana catching a Billygoat!"
 * yields Billygoat, and a player literally named Billygoat still resolves correctly.
 */
object ChatParser {

    sealed interface Event {
        /** You personally caught it. */
        data class Capture(val critter: Critter, val sparkling: Boolean) : Event

        /** A teammate caught it and you got the shard. Counts the same as a Capture for progress. */
        data class LootShare(val critter: Critter, val player: String, val sparkling: Boolean) : Event

        /** The lobby-wide SPARKLING broadcast line. */
        data class SparklingBroadcast(val critter: Critter, val player: String) : Event
    }

    // CAPTURE! You caught a <name>...  (whatever follows the name varies per critter)
    // Confirmed against a real line: "LOOT SHARE! You received 4x Hideyho Shard from
    // KeyWater1349 finding the Hideyho!" - Hideyho uses "found/finding" instead of
    // "caught/catching", and "the" instead of "a/an", since it's written as a named
    // character rather than a generic creature. Both forms are matched everywhere.
    private val CAPTURE_PREFIX = Regex("""^CAPTURE!\s+You (?:caught|found)\s+(?:(?:an?|the)\s+)?(SPARKLING\s+)?""")

    // LOOT SHARE! You received 2x Billygoat Shard from Xispana catching a <name>...
    private val LOOT_SHARE_PREFIX = Regex(
        """^LOOT SHARE!\s+You received\s+.+?\s+from\s+(.+?)\s+(?:catching|finding)\s+(?:(?:an?|the)\s+)?(SPARKLING\s+)?"""
    )

    // SPARKLING! Xispana caught a SPARKLING <name>...
    private val SPARKLING_PREFIX = Regex(
        """^SPARKLING!\s+(.+?)\s+(?:caught|found)\s+(?:(?:an?|the)\s+)?SPARKLING\s+"""
    )

    fun parse(rawLine: String): Event? {
        // Strip section-sign colour codes and collapse whitespace.
        val line = rawLine.replace(Regex("\u00A7."), "").trim().replace(Regex("\\s+"), " ")
        if (line.isEmpty()) return null

        CAPTURE_PREFIX.find(line)?.let { m ->
            val rest = line.substring(m.range.last + 1)
            val critter = Critters.matchAtStart(rest) ?: return null
            return Event.Capture(critter, m.groupValues[1].isNotEmpty())
        }

        LOOT_SHARE_PREFIX.find(line)?.let { m ->
            val rest = line.substring(m.range.last + 1)
            val critter = Critters.matchAtStart(rest) ?: return null
            return Event.LootShare(critter, m.groupValues[1], m.groupValues[2].isNotEmpty())
        }

        SPARKLING_PREFIX.find(line)?.let { m ->
            val rest = line.substring(m.range.last + 1)
            val critter = Critters.matchAtStart(rest) ?: return null
            return Event.SparklingBroadcast(critter, m.groupValues[1])
        }

        return null
    }
}
