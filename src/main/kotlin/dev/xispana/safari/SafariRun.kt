package dev.xispana.safari

/**
 * A single safari instance. Time is tracked in client ticks (SafariTracker.tick() calls
 * tick() below once per client tick) rather than wall-clock time, so a client-side lag
 * spike or freeze - which stalls tick processing along with everything else - doesn't
 * inflate the displayed timer. A tick is nominally 50ms (20 TPS).
 *
 * A capture and a loot share count identically toward progress - the only place they're
 * distinguished at all is the chat line that announces them. It doesn't matter whether you
 * or a teammate caught it; either way it's no longer missing for the team.
 *
 * [runNumber] is a simple 1-up count of every run started this session (SafariTracker hands
 * it out), independent of whether this run ends up "counted" toward SessionStats - it exists
 * so SessionShinyLog can say which numbered run a Sparkling critter turned up on ("run 13").
 */
class SafariRun(val runNumber: Int) {

    private var ticks: Long = 0L
    private var running: Boolean = true

    /** Every critter found this run, by you or a teammate, capture or loot share alike. */
    val found: LinkedHashSet<Critter> = LinkedHashSet()

    val sparkling: LinkedHashSet<Critter> = LinkedHashSet()

    /**
     * Every name credited with at least one find this run - "You" for your own catches, plus
     * whichever teammates' names showed up on a loot share or a SPARKLING broadcast. Used to
     * credit the whole party on the all-biomes-complete PB, not just whoever happened to catch
     * the one critter that tipped it over to "complete".
     */
    val contributors: LinkedHashSet<String> = LinkedHashSet()

    var totalCaptures: Int = 0
        private set
    var totalLootShares: Int = 0
        private set

    /** Milliseconds since the first sighting of each critter. */
    val firstCatchMs: LinkedHashMap<Critter, Long> = LinkedHashMap()

    /** Call once per client tick while this run should be counting. */
    fun tick() {
        if (running) ticks++
    }

    fun elapsedMs(): Long = ticks * MS_PER_TICK

    fun pause() {
        running = false
    }

    fun resume() {
        running = true
    }

    fun onCapture(critter: Critter, isSparkling: Boolean) {
        totalCaptures++
        recordFound(critter, isSparkling)
    }

    fun onLootShare(critter: Critter, isSparkling: Boolean) {
        totalLootShares++
        recordFound(critter, isSparkling)
    }

    fun onSparklingBroadcast(critter: Critter) {
        recordFound(critter, isSparkling = true)
    }

    private fun recordFound(critter: Critter, isSparkling: Boolean) {
        if (found.add(critter)) firstCatchMs.putIfAbsent(critter, elapsedMs())
        if (isSparkling) sparkling.add(critter)
    }

    // Unlike checkBiomeCompletion/biomeStats below, this does NOT exclude
    // Critters.OPTIONAL_FOR_COMPLETION (Macaw): a biome can finish - and announce as
    // complete - without it, but it should keep showing as missing (here and in the
    // party-chat announcer) until you've actually caught it, not disappear the moment the
    // rest of the biome is done.
    fun missing(): List<Critter> =
        Critters.ALL.filterNot { found.contains(it) }

    fun missingByBiome(): Map<Biome, List<Critter>> = missing().groupBy { it.biome }

    /** Per-biome found count, in a fixed biome order. Excludes OPTIONAL_FOR_COMPLETION critters. */
    fun biomeStats(): List<BiomeStat> = Biome.entries.map { biome ->
        val required = (Critters.BY_BIOME[biome] ?: emptyList()).filterNot { it in Critters.OPTIONAL_FOR_COMPLETION }
        BiomeStat(
            biome = biome,
            found = required.count { found.contains(it) },
            total = required.size
        )
    }

    data class BiomeStat(val biome: Biome, val found: Int, val total: Int)

    private val completedBiomes: MutableSet<Biome> = mutableSetOf()

    /**
     * Call right after a new sighting (capture or share). Returns true exactly once, the
     * moment [biome] hits 100% of its required (non-OPTIONAL_FOR_COMPLETION) critters -
     * false on every call before or after that moment.
     */
    fun checkBiomeCompletion(biome: Biome): Boolean {
        if (biome in completedBiomes) return false
        val required = (Critters.BY_BIOME[biome] ?: emptyList()).filterNot { it in Critters.OPTIONAL_FOR_COMPLETION }
        if (required.isEmpty() || required.any { !found.contains(it) }) return false
        completedBiomes.add(biome)
        return true
    }

    private var allBiomesAnnounced: Boolean = false

    /**
     * Call right after a checkBiomeCompletion(...) call returns true. Returns true exactly
     * once, the moment the last of the 4 biomes completes - false before or after.
     */
    fun checkAllBiomesComplete(): Boolean {
        if (allBiomesAnnounced) return false
        if (completedBiomes.size < Biome.entries.size) return false
        allBiomesAnnounced = true
        return true
    }

    private val firedAnnounceMarks: MutableSet<Int> = mutableSetOf()

    /** Returns true the first time [seconds] is reported for this run, false every time after. */
    fun markAnnounced(seconds: Int): Boolean = firedAnnounceMarks.add(seconds)

    /**
     * Guards SessionStats.recordCompletion against firing twice for the same run: once at the
     * instant all 4 biomes complete (if that happens), and again when the run ends. Whichever
     * happens first wins; the other is a no-op.
     */
    var sessionCounted: Boolean = false
        private set

    fun markSessionCounted() {
        sessionCounted = true
    }

    companion object {
        private const val MS_PER_TICK = 50L

        fun formatTime(ms: Long): String {
            val total = ms / 1000
            val h = total / 3600
            val m = (total % 3600) / 60
            val s = total % 60
            return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
        }
    }
}
