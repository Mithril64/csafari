package dev.xispana.safari

import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.world.scores.DisplaySlot
import net.minecraft.world.scores.PlayerTeam

/**
 * Reads the SkyBlock sidebar to work out where you are.
 *
 * Everything is wrapped defensively: if a scoreboard signature changes in a future
 * Minecraft version this degrades to "unknown location" instead of crashing, and you
 * can still drive the timer manually with /csafari start and /csafari stop.
 */
object LocationDetector {

    private const val SAFARI_MARKER = "critter safari"

    fun sidebarLines(mc: Minecraft): List<String> = runCatching {
        val scoreboard = mc.level?.scoreboard ?: return emptyList()
        val objective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR) ?: return emptyList()

        val lines = ArrayList<String>()
        lines.add(objective.displayName.string)

        // listPlayerScores() does NOT return entries in on-screen top-to-bottom order - that
        // was invisible as long as every caller only checked "does ANY line match", but it
        // broke MiriaTicketDetector's "look at the few lines right after the Miria header"
        // scoping, since the raw list order and the visual order aren't the same thing. The
        // vanilla sidebar itself sorts by score descending (ties broken by name, also
        // descending) to decide display order - mirrored here with two stable sorts (name
        // first, then score, so score is the primary key and name is only a tiebreaker) so
        // every line's position in this list now matches what's actually on screen.
        val sortedEntries = scoreboard.listPlayerScores(objective)
            .sortedByDescending { it.owner() }
            .sortedByDescending { it.value() }

        for (entry in sortedEntries) {
            val owner: String = entry.owner()
            val team: PlayerTeam? = scoreboard.getPlayersTeam(owner)
            val rendered: Component = PlayerTeam.formatNameForTeam(team, Component.literal(owner))
            lines.add(rendered.string.replace(Regex("\u00A7."), ""))
        }
        lines
    }.getOrDefault(emptyList())

    // Locations that must NEVER count as a Critter Safari run. The hub area around the safari
    // entrance (Torrhus Canyon / Foraging 3) has the sidebar location "Critter Safari Entrance" -
    // confirmed from a real screenshot - which contains "critter safari" and was being treated as
    // the safari itself, tracking its time as a run. (An earlier version blocked "torrhus canyon"
    // / "foraging 3" instead; those aren't what the sidebar actually says, so they never matched
    // anything and are gone.) Matched against every sidebar line (lowercased), and checked BEFORE
    // the safari marker, so being in a blocked location always wins.
    private val BLOCKED_LOCATIONS = listOf("critter safari entrance")

    private var lastBlockedLogged = false
    private val LOGGER = org.slf4j.LoggerFactory.getLogger("csafari-location")

    /** True while the sidebar says you are inside the Critter Safari (and not in a blocked hub). */
    fun inSafari(mc: Minecraft): Boolean {
        val lines = sidebarLines(mc).map { it.lowercase() }
        val blocked = lines.firstOrNull { line -> BLOCKED_LOCATIONS.any { line.contains(it) } }
        // Logged only when this flips, not every scan (inSafari runs ~2x/second), so latest.log
        // shows exactly which sidebar line blocked detection without flooding it.
        val isBlocked = blocked != null
        if (isBlocked != lastBlockedLogged) {
            lastBlockedLogged = isBlocked
            if (isBlocked) LOGGER.info("[csafari] not treating this as Critter Safari - blocked location line: '{}'", blocked)
        }
        if (isBlocked) return false
        return lines.any { it.contains(SAFARI_MARKER) }
    }

    /** True if the sidebar looks like SkyBlock at all (used to avoid false negatives on lag). */
    fun onSkyBlock(mc: Minecraft): Boolean =
        sidebarLines(mc).firstOrNull()?.lowercase()?.contains("skyblock") == true
}
