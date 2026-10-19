package dev.xispana.safari

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import java.nio.file.Files
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Reads the SkyBlock sidebar for Miria's Contest rarity-tier line - the screenshot you sent
 * showed "UNCOMMON with 389" / "Rare requires +611" - to work out whether this contest's
 * ticket is already secured. Per your report, Hypixel only guarantees a ticket once the tier
 * reaches UNCOMMON or higher; sitting at COMMON means nothing's secured yet.
 *
 * Reuses LocationDetector.sidebarLines (the same scoreboard read already proven working for
 * safari-location detection) rather than a new mixin, so this carries none of the usual
 * API-guessing risk - it's plain string matching over text that mod already reads safely.
 *
 * Caches the result for the rest of the 20-minute contest window once it's known, rather than
 * needing the sidebar line visible every single tick: a contest's tier only ever goes up, so
 * once it's been UNCOMMON+, it stays secured for that window even if you walk away from
 * Torrhus and the sidebar stops showing it. The cache is tagged with which contest window it
 * belongs to (the window's start time, to the minute) so it's automatically treated as stale
 * the moment the next contest starts, without needing any direct link to ContestTracker.
 * Persisted to disk (`critter-safari-miria-ticket.json`), so a game restart mid-contest still
 * remembers "secured" instead of forgetting until the sidebar is read again.
 *
 * Defaults to **not secured** (false) for a window until the sidebar actually proves
 * otherwise - per your request, the HUD line should default to showing red rather than
 * disappearing whenever this can't yet confirm either way (right after a restart, before the
 * sidebar's been glanced at, or simply before this contest's tier has cleared COMMON).
 */
object MiriaTicketDetector {

    // Matches a line like "UNCOMMON with 389" - word-boundaried tier name followed by
    // "with <number>", so it only fires on the actual tier-progress line and not some
    // unrelated sidebar text that happens to contain one of these words. Alternation order
    // doesn't matter for correctness here (word boundaries already stop "COMMON" matching
    // inside "UNCOMMON"), but listing longest-first keeps it obviously intentional.
    //
    // On its own this ALSO matches Agatha's Contest's tier line, which uses the exact same
    // wording - per your report that was false-flagging the ticket as secured off Agatha's
    // progress instead of Miria's. findMiriaTierLine() below is what actually scopes this to
    // only the lines directly under the sidebar's "Miria" header.
    private val TIER_LINE = Regex("(?i)\\b(LEGENDARY|EPIC|RARE|UNCOMMON|COMMON)\\b\\s+with\\s+\\d+")

    private const val SCAN_INTERVAL_TICKS = 10 // a couple of times a second is plenty
    private var tickCounter = 0

    private val WINDOW_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")

    private data class TicketState(val windowId: String, val secured: Boolean)

    private val gson: Gson = GsonBuilder().create()
    private val file = FabricLoader.getInstance().configDir.resolve("critter-safari-miria-ticket.json")

    /** Which 20-minute contest window the value below is valid for, or null before the first tick. */
    private var cachedWindowId: String? = null

    /** Defaults false (not secured) until a window's sidebar tier actually proves otherwise. */
    @Volatile
    var ticketSecured: Boolean = false
        private set

    init {
        load()
    }

    private fun currentWindowId(now: LocalDateTime): String =
        now.minusSeconds(ContestSchedule.secondsSinceLastMark(now.toLocalTime()))
            .format(WINDOW_FORMAT)

    fun tick(mc: Minecraft) {
        if (++tickCounter < SCAN_INTERVAL_TICKS) return
        tickCounter = 0

        val now = LocalDateTime.now()
        val windowId = currentWindowId(now)

        // A new contest window started since we last knew anything - whatever was cached
        // belonged to the previous one, so default back to "not secured" rather than carrying
        // a stale reading forward. No need to persist this reset: if the game restarts before
        // the sidebar's read again, load() below will see the stale saved window and land
        // here anyway.
        if (windowId != cachedWindowId) {
            cachedWindowId = windowId
            ticketSecured = false
        }

        val match = findMiriaTierLine(LocationDetector.sidebarLines(mc))
        val tier = match?.groupValues?.get(1)?.uppercase() ?: return // sidebar not showing it right now - keep the current value

        val secured = tier != "COMMON"
        if (ticketSecured == secured) return // no change - nothing new to persist
        ticketSecured = secured
        save()
        // Only on the false -> true transition, and only once (the guard above already
        // stops this from re-firing every tick while secured stays true).
        if (secured) {
            showSecuredTitle()
        }
        // Mirrors ContestTracker's own party-chat gating: only posts if actually in a party.
        if (secured && MiriaConfig.partyChatEnabled && PartyTracker.inParty) {
            Minecraft.getInstance().player?.connection?.sendCommand("pc Ticket secured!")
        }
    }

    /** On-screen title popup for the false -> true transition, so securing the ticket is
     *  impossible to miss even if you're not watching the HUD or sidebar right then. Not gated
     *  by partyChatEnabled - that toggle is specifically about the party-chat ping, this is a
     *  purely local, always-on notification. No subtitle (which contest it is is already
     *  obvious) and no fade-in (fadeInTicks=0) - it needs to be visible immediately, not ease in. */
    private fun showSecuredTitle() {
        val gui = Minecraft.getInstance().gui ?: return
        gui.setTitle(Component.literal("Ticket Secured!").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD))
        gui.setTimes(0, 40, 10)
    }

    /**
     * Scopes TIER_LINE to only the sidebar lines directly under the "Miria" header, instead of
     * matching the first "TIER with N" line anywhere on the whole sidebar - Agatha's Contest
     * has its own tier line in the exact same format, sitting elsewhere on the sidebar, and
     * was getting picked up as if it were Miria's. Stops scanning as soon as it hits a blank
     * line or one mentioning "Agatha", so it can't walk into that other section even if the
     * window below is generous.
     */
    private fun findMiriaTierLine(lines: List<String>): MatchResult? {
        val headerIndex = lines.indexOfFirst { it.contains("Miria", ignoreCase = true) }
        if (headerIndex == -1) return null
        for (i in (headerIndex + 1) until minOf(lines.size, headerIndex + 4)) {
            val line = lines[i]
            if (line.isBlank() || line.contains("Agatha", ignoreCase = true)) break
            TIER_LINE.find(line)?.let { return it }
        }
        return null
    }

    private fun load() {
        if (!Files.exists(file)) return
        runCatching {
            val state = gson.fromJson(Files.readString(file), TicketState::class.java) ?: return
            cachedWindowId = state.windowId
            ticketSecured = state.secured
        }
    }

    private fun save() {
        val windowId = cachedWindowId ?: return
        runCatching {
            Files.createDirectories(file.parent)
            Files.writeString(file, gson.toJson(TicketState(windowId, ticketSecured)))
        }
    }
}
